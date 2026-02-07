package moe.mlfc.onlinebot.commonenvdocker;

import moe.mlfc.onlinebot.commonenvdocker.configs.DockerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 自动清理任务
@EnableConfigurationProperties(DockerProperties.class)
public class CommonEnvDockerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonEnvDockerApplication.class, args);
    }

}
