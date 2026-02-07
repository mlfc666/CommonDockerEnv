package moe.mlfc.onlinebot.commonenvdocker.configs;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

    @Bean
    public DockerClient dockerClient(DockerProperties props) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(props.getHost())
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(20))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}