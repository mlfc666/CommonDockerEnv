package moe.mlfc.onlinebot.commonenvdocker.configs;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerNetworkSettings;
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

    @Bean
    public RouterFunction<ServerResponse> webAppProxyRoute() {
        return route("web_service")
                .route(RequestPredicates.path("/web-{sid}/**"), http())
                .before(request -> {
                    String sid = request.pathVariable("sid");
                    String fullContainerName = "env-" + sid;
                    String containerIp = getContainerIpBySid(fullContainerName);

                    if (containerIp == null) {
                        log.error("Web代理失败，找不到容器 IP: {}", fullContainerName);
                        return request;
                    }

                    // 获取原始路径，例如 "/web-e7af738c/index.html" 或 "/web-e7af738c"
                    String rawPath = request.uri().getPath();

                    // 剥离 "/web-{sid}" 前缀
                    String newPath = rawPath.replaceFirst("^/web-" + sid, "");

                    // 确保路径不为空。如果是空字符串，则设为根路径 "/"
                    if (newPath.isEmpty()) {
                        newPath = "/";
                    }

                    // 构造目标 URI，指向容器的 8080 端口
                    URI targetUri = UriComponentsBuilder.fromUriString("http://" + containerIp + ":8080")
                            .path(newPath)
                            .query(request.uri().getQuery()) // 保留查询参数
                            .build().toUri();

                    // 设置 Gateway 转发目标
                    request.attributes().put(MvcUtils.GATEWAY_REQUEST_URL_ATTR, targetUri);

                    log.info("Web 转发成功: {} -> {}", rawPath, targetUri);
                    return request;
                })
                .build();
    }

    private String getContainerIpBySid(String containerName) {
        try {
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singletonList(containerName)).exec();

            // 如果按原名找不到，尝试带斜杠的名称
            if (containers == null || containers.isEmpty()) {
                containers = dockerClient.listContainersCmd()
                        .withNameFilter(Collections.singletonList("/" + containerName)).exec();
            }

            // 使用 Optional 链式操作消除所有 null 检查
            return java.util.Optional.ofNullable(containers)
                    .filter(c -> !c.isEmpty())
                    .map(c -> c.get(0))
                    .map(Container::getNetworkSettings)
                    .map(ContainerNetworkSettings::getNetworks)
                    .map(networks -> networks.getOrDefault("1panel-network",
                            networks.values().stream().findFirst().orElse(null)))
                    .map(ContainerNetwork::getIpAddress)
                    .orElse(null);

        } catch (Exception e) {
            log.error("Docker 获取 IP 出错: {}", e.getMessage());
            return null;
        }
    }
}