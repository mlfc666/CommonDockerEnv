package moe.mlfc.onlinebot.commonenvdocker.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.docker")
public class DockerProperties {
    private String host;
    private String image;
    private String network;
    private String baseUrl;
    private int maxContainers;
    private long memoryLimit;
    private long cpuQuota;
    private long timeoutSeconds;
}