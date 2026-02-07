package moe.mlfc.onlinebot.commonenvdocker.configs;

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
        return route("ttyd_service")
                // 匹配 /env-{sid}/**，但不重写路径
                .route(RequestPredicates.path("/env-{sid}/**"), http())
                .before(request -> {
                    String sid = request.pathVariable("sid");
                    // 这里的 sid 是 env-xxxx
                    String containerIp = getContainerIpBySid(sid);

                    if (containerIp != null) {
                        // 直接转发，不修改 Path。
                        // 比如请求 /env-xxxx/static/js/main.js
                        // 转发到 http://172.18.0.x:7681/env-xxxx/static/js/main.js
                        // 这样 ttyd 就能正确识别了
                        return org.springframework.web.servlet.function.ServerRequest.from(request)
                                .uri(URI.create("http://" + containerIp + ":7681"))
                                .build();
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