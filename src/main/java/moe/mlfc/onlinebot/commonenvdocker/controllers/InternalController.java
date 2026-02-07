package moe.mlfc.onlinebot.commonenvdocker.controllers;

import lombok.extern.slf4j.Slf4j;
import moe.mlfc.onlinebot.commonenvdocker.services.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final DockerService dockerService;

    /**
     * Nginx 专用寻址接口：通过容器名获取内网 IP
     * 访问示例：GET /internal/get-ip?sid=env-aa488c0f
     */
    @GetMapping(value = "/get-ip", produces = "text/plain")
    public String getIp(@RequestParam String sid) {
        // 自动判断并补全 env- 前缀
        String fullSid = sid.startsWith("env-") ? sid : "env-" + sid;

        log.info("Nginx 正在查询容器 IP, SID: {}", fullSid);
        String ip = dockerService.getContainerIpBySid(fullSid);

        if (ip == null || ip.isEmpty()) {
            log.warn("未找到容器 IP, SID: {}", fullSid);
            return "";
        }
        return ip.trim();
    }
}