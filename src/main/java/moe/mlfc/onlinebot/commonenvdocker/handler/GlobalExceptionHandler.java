package moe.mlfc.onlinebot.commonenvdocker.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j // 建议加上日志记录，方便在后台查看真实报错
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleCapacity(IllegalStateException e) {
        log.warn("业务限制异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        // 在后台打印完整的堆栈信息，这是排查 500 错误的关键
        log.error("系统运行异常: ", e);

        String userMessage = "System busy, please try later.";

        // 如果是本地开发环境（可通过配置或 Profile 判断），可以附带详细信息
        // 目前简单处理：保留你原来的拼接，但建议在生产环境去掉 e.getMessage()
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", userMessage + " " + e.getMessage()));
    }
}