package moe.mlfc.onlinebot.commonenvdocker.configs;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Configuration
public class DynamicRouteConfig {

    private final DockerClient dockerClient;

    public DynamicRouteConfig(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Bean
    public RouterFunction<ServerResponse> ttydProxyRoute() {
        return route("ttyd_service")
                .route(RequestPredicates.path("/env-{sid}/**"), http())
                .before(request -> {
                    String sid = request.pathVariable("sid");
                    String fullContainerName = "env-" + sid;
                    String containerIp = getContainerIpBySid(fullContainerName);

                    if (containerIp == null) {
                        log.error("无法找到容器 IP: {}", fullContainerName);
                        return request;
                    }

                    // 如果原始请求是 wss 或 ws，目标也应该是 ws
                    String upgradeHeader = request.headers().firstHeader("Upgrade");
                    String scheme = (upgradeHeader != null && upgradeHeader.equalsIgnoreCase("websocket"))
                            ? "ws" : "http";

                    URI targetUri = UriComponentsBuilder.fromUriString(scheme + "://" + containerIp + ":7681")
                            .build().toUri();

                    // 必须设置这个属性，Gateway MVC 才能正确执行转发
                    request.attributes().put(MvcUtils.GATEWAY_REQUEST_URL_ATTR, targetUri);

                    log.info("WebSocket/HTTP 转发: {} -> {}", request.path(), targetUri);
                    return request;
                })
                .build();
    }

    private String getContainerIpBySid(String containerName) {
        try {
            // Docker 容器名在 API 中通常带有前缀 /
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singletonList(containerName))
                    .exec();

            if (containers == null || containers.isEmpty()) {
                // 尝试模糊匹配，防止名字里带斜杠的问题
                containers = dockerClient.listContainersCmd()
                        .withNameFilter(Collections.singletonList("/" + containerName))
                        .exec();
            }

            if (containers == null || containers.isEmpty()) return null;

            Container container = containers.get(0);
            Map<String, ContainerNetwork> networks = container.getNetworkSettings().getNetworks();

            // 从图片看，你的网络应该是 1panel-network
            ContainerNetwork net = networks.get("1panel-network");
            if (net == null) {
                // 备选计划：取第一个可用的网络
                net = networks.values().stream().findFirst().orElse(null);
            }

            return (net != null) ? net.getIpAddress() : null;
        } catch (Exception e) {
            log.error("Docker 获取 IP 出错: {}", e.getMessage());
            return null;
        }
    }
}