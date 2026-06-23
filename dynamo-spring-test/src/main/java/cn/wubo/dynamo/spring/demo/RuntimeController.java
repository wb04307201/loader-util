package cn.wubo.dynamo.spring.demo;

import cn.wubo.dynamo.spring.DynamoRuntime;
import cn.wubo.dynamo.spring.exception.CompilationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 4：DynamoRuntime 高级门面。把动态编译 + 类路径管理 + Bean 注册粘合到一次生命周期内。
 *
 * <p>每个 session 对应一个 {@link DynamoRuntime}（含独立 ClassLoader + 可选 BeanFactory）。
 * session 内 compile 出来的类在 session close 时随 ClassLoader 一起释放。
 */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {

    private final InstanceRegistry registry;
    private final ApplicationContext ctx;

    public RuntimeController(InstanceRegistry registry, ApplicationContext ctx) {
        this.registry = registry;
        this.ctx = ctx;
    }

    private DefaultListableBeanFactory bf() {
        return (DefaultListableBeanFactory) ((ConfigurableApplicationContext) ctx).getBeanFactory();
    }

    // ---------- session 管理 ----------

    @PostMapping("/sessions")
    public Map<String, Object> createSession() {
        DynamoRuntime rt = DynamoRuntime.withBeanFactory(bf());
        String id = registry.putRuntime(rt);
        return Map.of("sessionId", id);
    }

    @GetMapping("/sessions")
    public List<String> listSessions() {
        return registry.listRuntimes();
    }

    @DeleteMapping("/sessions/{id}")
    public Map<String, Object> closeSession(@PathVariable String id) {
        return Map.of("closed", registry.removeRuntime(id), "sessionId", id);
    }

    // ---------- session 内操作 ----------

    @PostMapping("/sessions/{id}/compile")
    public ResponseEntity<Map<String, Object>> compile(@PathVariable String id,
                                                       @RequestBody SourceRequest req) {
        if (req.source == null || req.source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }
        DynamoRuntime rt = registry.getRuntime(id);
        try {
            Class<?> clazz = rt.compileAndLoad(req.source);
            String classId = registry.putRuntimeClass(id, clazz);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("classId", classId);
            resp.put("className", clazz.getName());
            return ResponseEntity.ok(resp);
        } catch (CompilationException ex) {
            return ResponseEntity.status(422).body(Map.of(
                "error", ex.getMessage(),
                "diagnostics", ex.getResult() == null
                    ? List.of()
                    : ex.getResult().getDiagnostics().stream().map(Object::toString).toList()
            ));
        }
    }

    @PostMapping("/sessions/{id}/instantiate")
    public ResponseEntity<Map<String, Object>> instantiate(@PathVariable String id,
                                                           @RequestBody ClassIdRequest req) {
        Class<?> clazz = registry.getRuntimeClass(id, req.classId);
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            Object instance = ctor.newInstance();
            String instanceId = registry.putRuntimeInstance(id, instance);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("instanceId", instanceId);
            resp.put("className", clazz.getName());
            return ResponseEntity.ok(resp);
        } catch (NoSuchMethodException e) {
            return ResponseEntity.status(422).body(Map.of(
                "error", "类必须有无参构造方法: " + clazz.getName()
            ));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "实例化失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/sessions/{id}/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@PathVariable String id,
                                                      @RequestBody InvokeRequest req) {
        Object instance = registry.getRuntimeInstance(id, req.instanceId);
        Method method = ArgsHelper.findMethod(instance.getClass(), req.methodName,
            req.args == null ? 0 : req.args.size());
        if (method == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "找不到方法: " + req.methodName
            ));
        }
        try {
            Object[] typedArgs = ArgsHelper.coerceArgs(method.getParameterTypes(), req.args);
            Object result = method.invoke(instance, typedArgs);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("result", result == null ? null : String.valueOf(result));
            resp.put("resultType", method.getReturnType().getName());
            return ResponseEntity.ok(resp);
        } catch (IllegalAccessException e) {
            return ResponseEntity.status(500).body(Map.of("error", "无法访问方法: " + e.getMessage()));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return ResponseEntity.status(500).body(Map.of(
                "error", "方法执行异常: " + cause.getClass().getName() + ": " + cause.getMessage()
            ));
        }
    }

    @PostMapping("/sessions/{id}/registerController")
    public ResponseEntity<Map<String, Object>> registerController(@PathVariable String id,
                                                                  @RequestBody RegisterControllerRequest req) {
        if (req.beanName == null || req.beanName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "beanName 不能为空"));
        }
        if (req.source == null || req.source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }
        DynamoRuntime rt = registry.getRuntime(id);
        Class<?> clazz;
        try {
            clazz = rt.compileAndLoad(req.source);
        } catch (CompilationException ex) {
            return ResponseEntity.status(422).body(Map.of(
                "error", ex.getMessage(),
                "diagnostics", ex.getResult() == null
                    ? List.of()
                    : ex.getResult().getDiagnostics().stream().map(Object::toString).toList()
            ));
        }
        try {
            rt.registerController(req.beanName, clazz);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "注册失败: " + ex.getMessage()
            ));
        }
        registry.trackRuntimeController(id, req.beanName);
        // 顺便把类也加到 class 池（用户可能想再 instantiate 一次）
        registry.putRuntimeClass(id, clazz);

        // 反射读 routes（与 BeanController 同源逻辑）
        List<Map<String, Object>> routes = BeanControllerHelper.routesForBean(ctx, req.beanName);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("beanName", req.beanName);
        resp.put("className", clazz.getName());
        resp.put("routes", routes);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/sessions/{id}/unregisterController")
    public Map<String, Object> unregisterController(@PathVariable String id,
                                                     @RequestBody BeanNameRequest req) {
        DynamoRuntime rt = registry.getRuntime(id);
        try {
            rt.unregisterController(req.beanName);
        } catch (RuntimeException ex) {
            return Map.of("removed", false, "error", ex.getMessage());
        }
        registry.untrackRuntimeController(id, req.beanName);
        return Map.of("removed", true, "beanName", req.beanName);
    }

    @GetMapping("/sessions/{id}/state")
    public Map<String, Object> state(@PathVariable String id) {
        // 简单校验 session 存在
        registry.getRuntime(id);
        return Map.of(
            "sessionId", id,
            "classes", registry.listRuntimeClasses(id),
            "instances", registry.listRuntimeInstances(id),
            "controllers", registry.getRuntimeControllers(id)
        );
    }

    // ---------- helpers ----------


    // ---------- DTO ----------

    public static class SourceRequest {
        public String source;
    }

    public static class ClassIdRequest {
        public String classId;
    }

    public static class InvokeRequest {
        public String instanceId;
        public String methodName;
        public List<?> args;
    }

    public static class RegisterControllerRequest {
        public String beanName;
        public String source;
    }

    public static class BeanNameRequest {
        public String beanName;
    }
}
