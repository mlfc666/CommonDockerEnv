package moe.mlfc.onlinebot.commonenvdocker.controllers;

import jakarta.servlet.http.HttpServletRequest;
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
    @GetMapping("/get-ip")
    public String getIp(@RequestParam String sid, HttpServletRequest request) {
        // 安全限制：仅允许宿主机 127.0.0.1 访问该接口
        String remoteAddr = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr) && !"0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return "";
        }

        // 调用你 DockerService 里的 getContainerIpBySid
        String ip = dockerService.getContainerIpBySid(sid);
        return (ip != null) ? ip : "";
    }
}