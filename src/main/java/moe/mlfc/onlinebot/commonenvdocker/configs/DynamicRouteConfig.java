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
                // 注意：这里我们匹配整个 /env-xxxx 路径
                .route(RequestPredicates.path("/env-{sid}/**"), http())
                .before(request -> {
                    String sid = request.pathVariable("sid");

                    // 【修正 1】：容器名需要加上 env- 前缀，因为图片显示容器名是 env-777b12fc
                    String fullContainerName = "env-" + sid;
                    String containerIp = getContainerIpBySid(fullContainerName);

                    if (containerIp == null) {
                        log.error("路由转发失败：找不到容器 {} 的 IP", fullContainerName);
                        // 如果找不到 IP，不再返回 request 导致 404/500，这里让它由于没有 URI 而自然报错或你可以抛出异常
                        return request;
                    }

                    // 【修正 2】：构建目标 URI。ttyd 已经在容器内监听 7681
                    // 保持路径完整转发（不重写），因为 ttyd 启动参数带了 -b /env-xxxx
                    URI targetUri = URI.create("http://" + containerIp + ":7681");

                    log.info("转发成功: {} -> {}", request.path(), targetUri);

                    // 【修正 3】：这是 Gateway MVC 必须设置的属性，用于告诉底层的 http() 处理器去哪里
                    request.attributes().put(MvcUtils.GATEWAY_REQUEST_URL_ATTR, targetUri);

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