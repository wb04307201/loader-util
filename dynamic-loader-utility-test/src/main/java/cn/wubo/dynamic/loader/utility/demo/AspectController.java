package cn.wubo.dynamic.loader.utility.demo;

import cn.wubo.dynamic.loader.utility.aspect.DynamicProxy;
import cn.wubo.dynamic.loader.utility.aspect.IAdvice;
import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;
import cn.wubo.dynamic.loader.utility.demo.advice.LoggingAdvice;
import cn.wubo.dynamic.loader.utility.demo.advice.ThrowAdvice;
import cn.wubo.dynamic.loader.utility.demo.advice.TimingAdvice;
import cn.wubo.dynamic.loader.utility.exception.CompilationException;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 2：动态编译 + ByteBuddy 代理。选 advice 类型后创建代理对象；调用方法
 * 时按 {@code before → method → after / afterThrow} 顺序触发，并把日志返回给前端。
 */
@RestController
@RequestMapping("/api/aspect")
public class AspectController {

    private final InstanceRegistry registry;

    public AspectController(InstanceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/proxy")
    public ResponseEntity<Map<String, Object>> proxy(@RequestBody ProxyRequest req) {
        if (req.source == null || req.source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }
        String adviceType = req.adviceType == null ? "log" : req.adviceType.toLowerCase();
        if (!adviceType.equals("log") && !adviceType.equals("timing") && !adviceType.equals("throw")) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "adviceType 必须是 log / timing / throw 之一"
            ));
        }

        DynamicClassLoader loader = DynamicClassLoader.create();
        Class<?> clazz;
        try {
            clazz = loader.compileAndLoad(req.source);
        } catch (CompilationException ex) {
            loader.close();
            return ResponseEntity.status(422).body(Map.of(
                "error", ex.getMessage(),
                "diagnostics", ex.getResult() == null
                    ? List.of()
                    : ex.getResult().getDiagnostics().stream().map(Object::toString).toList()
            ));
        }

        Object target;
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            target = ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            loader.close();
            return ResponseEntity.status(422).body(Map.of(
                "error", "类必须有无参构造方法: " + clazz.getName()
            ));
        }

        Deque<Map<String, Object>> log = InstanceRegistry.newAdviceLog();
        IAdvice advice = switch (adviceType) {
            case "timing" -> new TimingAdvice(log);
            case "throw" -> new ThrowAdvice(log);
            default -> new LoggingAdvice(log);
        };

        Object proxy;
        try {
            // 用同一个 loader 作为 ByteBuddy 注入目标 ClassLoader，
            // 保证代理类能找到目标类
            proxy = DynamicProxy.proxy(clazz, advice, loader);
        } catch (RuntimeException ex) {
            loader.close();
            return ResponseEntity.status(500).body(Map.of("error", "代理失败: " + ex.getMessage()));
        }

        String id = registry.putAspect(proxy, log, loader);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("className", clazz.getName());
        resp.put("adviceType", adviceType);
        resp.put("methods", describeMethods(clazz));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody InvokeRequest req) {
        Object proxy = registry.getAspect(req.id);
        Method method = ArgsHelper.findMethod(proxy.getClass(), req.methodName,
            req.args == null ? 0 : req.args.size());
        if (method == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "找不到方法: " + req.methodName
            ));
        }

        // 调用前记一个快照，便于 UI 区分"本次调用产生的新日志"
        Deque<Map<String, Object>> log = registry.getAspectLog(req.id);
        int sizeBefore = log.size();

        try {
            Object[] typedArgs = ArgsHelper.coerceArgs(method.getParameterTypes(), req.args);
            Object result = method.invoke(proxy, typedArgs);
            List<Map<String, Object>> snapshot = new ArrayList<>(log);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("result", result == null ? null : String.valueOf(result));
            resp.put("resultType", method.getReturnType().getName());
            resp.put("logs", snapshot);
            resp.put("newLogCount", snapshot.size() - sizeBefore);
            return ResponseEntity.ok(resp);
        } catch (IllegalAccessException e) {
            return ResponseEntity.status(500).body(Map.of("error", "无法访问方法: " + e.getMessage()));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            List<Map<String, Object>> snapshot = new ArrayList<>(log);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "advice 抛出: " + cause.getClass().getName() + ": " + cause.getMessage());
            resp.put("logs", snapshot);
            resp.put("newLogCount", snapshot.size() - sizeBefore);
            // 500 表示：afterThrow 被触发（advice 不吞异常，ByteBuddy 重新抛出）
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/proxies")
    public List<Map<String, Object>> proxies() {
        return registry.listAspect();
    }

    @GetMapping("/proxies/{id}/logs")
    public Map<String, Object> logs(@PathVariable String id) {
        return Map.of("id", id, "logs", new ArrayList<>(registry.getAspectLog(id)));
    }

    @DeleteMapping("/proxies/{id}")
    public Map<String, Object> remove(@PathVariable String id) {
        return Map.of("removed", registry.removeAspect(id));
    }

    // ---------- helpers ----------

    private static List<Map<String, Object>> describeMethods(Class<?> clazz) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", m.getName());
            entry.put("returnType", m.getReturnType().getName());
            entry.put("paramTypes", java.util.Arrays.stream(m.getParameterTypes())
                .map(Class::getName).toList());
            list.add(entry);
        }
        return list;
    }

    // ---------- DTO ----------

    public static class ProxyRequest {
        public String source;
        public String adviceType;
    }

    public static class InvokeRequest {
        public String id;
        public String methodName;
        public List<?> args;
    }
}
