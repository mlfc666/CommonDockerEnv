package moe.mlfc.onlinebot.commonenvdocker.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moe.mlfc.onlinebot.commonenvdocker.configs.DockerProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private final DockerClient dockerClient;
    private final DockerProperties props;

    public String createEnvironment() {
        checkCapacity();
        String imageTag = props.getImage();
        ensureImageExists(imageTag);

        // 这里的 sid 仅作为随机标识
        String rawId = UUID.randomUUID().toString().substring(0, 8);
        String sid = "env-" + rawId; // 最终容器名：env-xxxx

        boolean isLocal = props.getBaseUrl().contains("localhost");

        // 生产环境下的 basePath 必须严格对应 Nginx 访问的路径
        // 如果 Nginx 访问 https://domain.com/env-xxxx/
        // 那么 ttyd 的 basePath 必须是 /env-xxxx (末尾不带斜杠，ttyd 会自动处理)
        String basePath = isLocal ? "" : "/" + sid;

        long expirationTimestamp = System.currentTimeMillis() + (props.getTimeoutSeconds() * 1000);

        try {
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode(determineNetwork())
                    .withAutoRemove(true)
                    .withMemory(props.getMemoryLimit())
                    .withMemorySwap(props.getMemoryLimit())
                    .withCpuQuota(props.getCpuQuota());

            // 生产环境不映射端口，走内网 IP 转发
            if (isLocal) {
                hostConfig.withPortBindings(PortBinding.parse("9999:7681"));
            }

            CreateContainerResponse container = dockerClient.createContainerCmd(imageTag)
                    .withName(sid) // 容器名 env-xxxx
                    .withHostConfig(hostConfig)
                    .withEntrypoint("ttyd")
                    .withEnv("EXPIRATION_TIME=" + expirationTimestamp)
                    // 这里传入 sid 和处理后的 basePath
                    .withCmd(buildTtydArgs(sid, basePath))
                    .exec();

            dockerClient.startContainerCmd(container.getId()).exec();

            log.info("环境已就绪: {} -> {}{}/", sid, props.getBaseUrl(), sid);
            return sid;
        } catch (Exception e) {
            log.error("容器启动失败", e);
            throw new RuntimeException("服务启动失败 " + e.getMessage());
        }
    }

    private String[] buildTtydArgs(String sid, String basePath) {
        // 显式设置环境变量 TERM 和 LANG，确保字符集和颜色正确
        String envPrefix = "export TERM=xterm-256color; export LANG=C.UTF-8; ";
        String javaCmd = "java -Xmx192m -Xms128m -XX:+UseSerialGC -jar /app/app.jar";

        return new String[]{
                "-p", "7681",
                "-b", basePath,
                "-W",
                "-a",
                "-t", "titleFixed=" + sid,
                "-t", "fontSize=15",
                "-t", "lineHeight=1.3",
                "-t", "cursorStyle=underline",
                "-t", "cursorBlink=true",
                "-t", "padding=4",
                "-t", "enableSixel=true",
                "-t", "scrollback=2000",
                "-t", "scrollOnUserInput=true",
                "-t", "scrollOnOutput=true",
                "-t", "theme=" + DRACULA_THEME,

                // 加上 -2 参数，强制 tmux 开启 256 色模式
                "tmux", "-2", "new-session", "-A", "-s", "env_" + sid,

                // 在启动 java 之前先执行环境配置
                "stty intr undef; tmux set -g mouse on; tmux set status off; " +
                        "tmux bind-key -n C-c send-keys ''; " +
                        envPrefix + javaCmd
        };
    }

    @Scheduled(fixedRate = 60000)
    public void autoCleanup() {
        long now = System.currentTimeMillis();
        dockerClient.listContainersCmd().exec().stream()
                .filter(c -> c.getNames()[0].startsWith("/env-"))
                .forEach(c -> {
                    try {
                        var inspect = dockerClient.inspectContainerCmd(c.getId()).exec();
                        String[] envs = inspect.getConfig().getEnv();

                        // 解析环境变量获取过期时间
                        long expireAt = 0L;
                        if (envs != null) {
                            expireAt = Arrays.stream(envs)
                                    .filter(e -> e.startsWith("EXPIRATION_TIME="))
                                    .map(e -> Long.parseLong(e.split("=")[1]))
                                    .findFirst().orElse(0L);
                        }

                        if (expireAt > 0 && now > expireAt) {
                            dockerClient.removeContainerCmd(c.getId()).withForce(true).exec();
                            log.info("已回收超时容器 {}", c.getNames()[0]);
                        }
                    } catch (Exception e) {
                        log.warn("检查容器过期失败 ID为 {}", c.getId());
                    }
                });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanUpOnStart() {
        log.info("系统启动并执行初始化清理");
        try {
            dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(c -> c.getNames()[0].startsWith("/env-"))
                    .forEach(c -> {
                        try {
                            dockerClient.removeContainerCmd(c.getId()).withForce(true).exec();
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception e) {
            log.error("启动清理失败", e);
        }
    }

    private String determineNetwork() {
        try {
            boolean exists = dockerClient.listNetworksCmd().exec().stream()
                    .anyMatch(n -> n.getName().equals(props.getNetwork()));
            return exists ? props.getNetwork() : "bridge";
        } catch (Exception e) {
            return "bridge";
        }
    }

    private void ensureImageExists(String imageTag) {
        try {
            dockerClient.inspectImageCmd(imageTag).exec();
            log.info("本地已存在镜像: {}", imageTag);
        } catch (Exception e) {
            // 本地不存在时，才执行拉取动作
            log.info("本地不存在镜像，正在尝试从远程拉取: {}", imageTag);
            try {
                dockerClient.pullImageCmd(imageTag)
                        .start()
                        .awaitCompletion(5, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("拉取镜像被中断");
            } catch (Exception ex) {
                log.error("镜像拉取失败: {}", imageTag);
                throw new RuntimeException("无法获取所需镜像: " + imageTag);
            }
        }
    }

    private void checkCapacity() {
        long count = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                .filter(c -> c.getNames()[0].startsWith("/env-")).count();
        if (count >= props.getMaxContainers()) {
            throw new IllegalStateException("当前人数已满");
        }
    }

    public boolean isContainerRunning(String sid) {
        try {
            dockerClient.inspectContainerCmd(sid).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getRemainingSeconds(String sid) {
        try {
            var inspect = dockerClient.inspectContainerCmd(sid).exec();
            String[] envs = inspect.getConfig().getEnv();
            if (envs == null) return 0;

            long expireAt = Arrays.stream(envs)
                    .filter(e -> e.startsWith("EXPIRATION_TIME="))
                    .map(e -> Long.parseLong(e.split("=")[1]))
                    .findFirst().orElse(0L);

            long remaining = (expireAt - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public void destroyEnvironment(String sid) {
        if (sid == null || sid.isEmpty()) return;
        try {
            // 强制移除容器确保资源释放
            dockerClient.removeContainerCmd(sid).withForce(true).exec();
            log.info("容器已成功销毁 {}", sid);
        } catch (Exception e) {
            log.warn("销毁容器时发生异常 {}", e.getMessage());
        }
    }

    // 根据容器名称获取其在指定网络中的内网 IP
    public String getContainerIpBySid(String sid) {
        try {
            // 1获取容器列表，使用名称过滤
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(java.util.Collections.singletonList(sid))
                    .exec();

            if (containers == null || containers.isEmpty()) {
                log.warn("未找到名为 {} 的容器", sid);
                return null;
            }

            // 获取第一个匹配的容器元数据
            var container = containers.get(0);
            var networkSettings = container.getNetworkSettings();
            if (networkSettings == null) return null;

            var networks = networkSettings.getNetworks();
            if (networks == null || networks.isEmpty()) return null;

            // 优先获取配置文件中指定的网络 IP (如 1panel-network)
            var targetNetwork = networks.get(props.getNetwork());

            // 如果找不到指定网络，则尝试获取第一个可用的网络 IP
            if (targetNetwork == null) {
                targetNetwork = networks.values().iterator().next();
            }

            String ip = targetNetwork.getIpAddress();
            log.debug("获取容器 {} IP 成功: {}", sid, ip);
            return ip;
        } catch (Exception e) {
            log.error("获取容器 {} IP 时发生异常: {}", sid, e.getMessage());
            return null;
        }
    }

    // 终端主题配色配置
    private static final String DRACULA_THEME = "{" +
            "'background': '#282a36'," +
            "'foreground': '#f8f8f2'," +
            "'cursor': '#ff79c6'," +
            "'selection': '#44475a'," +
            "'black': '#21222c'," +
            "'red': '#ff5555'," +
            "'green': '#50fa7b'," +
            "'yellow': '#f1fa8c'," +
            "'blue': '#bd93f9'," +
            "'magenta': '#ff79c6'," +
            "'cyan': '#8be9fd'," +
            "'white': '#f8f8f2'," +
            "'brightBlack': '#6272a4'," +
            "'brightRed': '#ff6e6e'," +
            "'brightGreen': '#69ff94'," +
            "'brightYellow': '#ffffa5'," +
            "'brightBlue': '#d6acff'," +
            "'brightMagenta': '#ff92df'," +
            "'brightCyan': '#a4ffff'," +
            "'brightWhite': '#ffffff'" +
            "}";
}