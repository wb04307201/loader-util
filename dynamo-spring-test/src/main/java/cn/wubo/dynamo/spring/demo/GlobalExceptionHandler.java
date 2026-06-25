package cn.wubo.dynamo.spring.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * demo 应用的全局异常处理：把未捕获异常的 message + className + 部分 stack 返回给前端，
 * 便于在 UI 上调试（生产环境不会暴露这些信息）。
 *
 * <p>404 类异常（NoHandlerFoundException / NoResourceFoundException）单独走 404 分支，
 * 否则会被默认 Exception 分支兜成 500。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Not Found");
        body.put("path", ex.getResourcePath());
        return ResponseEntity.status(404).body(body);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandler(NoHandlerFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", "Not Found"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIAE(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getClass().getName() + ": " + ex.getMessage());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        log.error("demo endpoint threw", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        body.put("error", ex.getClass().getName() + ": " + ex.getMessage());
        body.put("cause", cause.getClass().getName() + ": " + cause.getMessage());
        StackTraceElement[] stack = ex.getStackTrace();
        int n = Math.min(10, stack.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(stack[i].toString()).append('\n');
        }
        body.put("stackHead", sb.toString());
        return ResponseEntity.status(500).body(body);
    }
}
