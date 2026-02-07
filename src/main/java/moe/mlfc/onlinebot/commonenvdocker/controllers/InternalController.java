package moe.mlfc.onlinebot.commonenvdocker.controllers;

import moe.mlfc.onlinebot.commonenvdocker.services.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {
    @Autowired
    private DockerService dockerService;

    @GetMapping(value = "/get-ip")
    public ResponseEntity<String> getIp(@RequestParam("sid") String sid) {
        String fullSid = sid.startsWith("env-") ? sid : "env-" + sid;
        String ip = dockerService.getContainerIpBySid(fullSid);

        if (ip == null || ip.isEmpty()) {
            return ResponseEntity.status(404).body("NOT_FOUND");
        }

        // 关键点：将 IP 放入自定义响应头 X-Target-IP 中
        return ResponseEntity.ok()
                .header("X-Target-IP", ip.trim())
                .body(ip.trim());
    }
}