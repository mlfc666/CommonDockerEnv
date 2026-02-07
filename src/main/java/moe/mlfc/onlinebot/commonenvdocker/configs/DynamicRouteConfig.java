package moe.mlfc.onlinebot.commonenvdocker.configs;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

@Configuration
public class DynamicRouteConfig {

    private final DockerClient dockerClient;

    // 修复报错 1：使用构造器注入，解决 final 字段未初始化问题
    public DynamicRouteConfig(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Bean
    public RouterFunction<ServerResponse> ttydProxyRoute() {
        // 修复报错 2：调整 Gateway MVC 的链式调用顺序
        // 在 Gateway MVC 中，handler (http()) 是作为 route 的参数传入的
        return route("ttyd_service")
                .route(RequestPredicates.path("/env-{sid}/**"), http())
                .filter(rewritePath("/env-(?<sid>[^/]+)/(?<remaining>.*)", "/${remaining}"))
                .before(request -> {
                    try {
                        String sid = request.pathVariable("sid");
                        String containerIp = getContainerIpBySid("env-" + sid);

                        if (containerIp != null) {
                            // 动态构建目标 URI
                            URI targetUri = URI.create("http://" + containerIp + ":7681");
                            return org.springframework.web.servlet.function.ServerRequest.from(request)
                                    .uri(targetUri)
                                    .build();
                        }
                    } catch (IllegalArgumentException e) {
                        // 如果 pathVariable 中没有 sid，直接返回原请求
                    }
                    return request;
                })
                .build();
    }

    private String getContainerIpBySid(String containerName) {
        try {
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singletonList(containerName))
                    .exec();

            if (containers == null || containers.isEmpty()) {
                return null;
            }

            Container container = containers.get(0);

            // 修复报错 3：防御性编程，防止 getNetworkSettings 或 getNetworks 为 null
            if (container.getNetworkSettings() != null && container.getNetworkSettings().getNetworks() != null) {
                Map<String, ContainerNetwork> networks = container.getNetworkSettings().getNetworks();
                ContainerNetwork targetNetwork = networks.get("1panel-network");
                if (targetNetwork != null) {
                    return targetNetwork.getIpAddress();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}