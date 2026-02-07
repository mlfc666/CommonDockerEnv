package moe.mlfc.onlinebot.commonenvdocker.controllers;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import moe.mlfc.onlinebot.commonenvdocker.configs.DockerProperties;
import moe.mlfc.onlinebot.commonenvdocker.services.DockerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DockerController {

    private final DockerService dockerService;
    private final DockerProperties props;


    @PostMapping("/spawn")
    public Map<String, Object> spawn(HttpSession session) {
        // 这里依然保留逻辑：如果有旧的返回旧的，没有才建新的
        String existingSid = (String) session.getAttribute("current_sid");
        if (existingSid != null && dockerService.isContainerRunning(existingSid)) {
            return buildResponse(existingSid);
        }

        String sid = dockerService.createEnvironment();
        session.setAttribute("current_sid", sid);
        return buildResponse(sid);
    }
    // 在 DockerController.java 中添加
    @PostMapping("/destroy")
    public Map<String, Object> destroy(HttpSession session) {
        String sid = (String) session.getAttribute("current_sid");

        if (sid != null) {
            // 调用 Service 层的逻辑
            dockerService.destroyEnvironment(sid);
            // 清理 Session 记录
            session.removeAttribute("current_sid");
        }

        return Map.of("success", true);
    }

    // 补充：为了让前端能判断状态，建议也加上这个 GET 接口
    @GetMapping("/check")
    public Map<String, Object> checkStatus(HttpSession session) {
        String sid = (String) session.getAttribute("current_sid");
        if (sid != null && dockerService.isContainerRunning(sid)) {
            // 返回与 spawn 接口结构一致的数据
            boolean isLocal = props.getBaseUrl().contains("localhost");
            String targetUrl = isLocal ? props.getBaseUrl() : props.getBaseUrl() + sid + "/";
            return Map.of(
                    "sid", sid,
                    "url", targetUrl,
                    "timeout", dockerService.getRemainingSeconds(sid),
                    "active", true
            );
        }
        return Map.of("active", false);
    }

    private Map<String, Object> buildResponse(String sid) {
        boolean isLocal = props.getBaseUrl().contains("localhost");
        String url = isLocal ? props.getBaseUrl() : props.getBaseUrl() + sid + "/";
        return Map.of(
                "sid", sid,
                "url", url,
                "timeout", dockerService.getRemainingSeconds(sid)
        );
    }
}