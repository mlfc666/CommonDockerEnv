package moe.mlfc.onlinebot.commonenvdocker.controllers;

import moe.mlfc.onlinebot.commonenvdocker.services.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final DockerService dockerService;

    /**
     * Nginx 专用寻址接口：通过容器名获取内网 IP
     * 访问示例：GET /internal/get-ip?sid=env-aa488c0f
     */
    @GetMapping(value = "/get-ip", produces = "text/plain") // 1. 强制纯文本
    public String getIp(@RequestParam String sid) {
        String ip = dockerService.getContainerIpBySid(sid);
        if (ip == null || ip.isEmpty()) {
            return "ERROR"; // 2. 不要返回空，返回一个可识别的错误字符串
        }
        return ip.trim(); // 3. 必须 trim() 去掉换行符
    }
}