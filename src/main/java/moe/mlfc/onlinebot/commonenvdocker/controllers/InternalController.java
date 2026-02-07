package moe.mlfc.onlinebot.commonenvdocker.controllers;

import moe.mlfc.onlinebot.commonenvdocker.services.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {
    @Autowired
    private DockerService dockerService;

    @GetMapping(value = "/get-ip", produces = "text/plain;charset=UTF-8")
    public String getIp(@RequestParam("sid") String sid) {
        // 1. 补全前缀逻辑
        String fullSid = sid.startsWith("env-") ? sid : "env-" + sid;

        // 2. 获取 IP
        String ip = dockerService.getContainerIpBySid(fullSid);

        // 3. 结果返回：必须是纯净的 IP 字符串
        if (ip == null || ip.isEmpty()) {
            return "NOT_FOUND";
        }
        return ip.trim();
    }
}