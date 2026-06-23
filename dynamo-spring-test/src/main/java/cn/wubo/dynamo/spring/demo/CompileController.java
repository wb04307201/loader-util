package cn.wubo.dynamo.spring.demo;

import cn.wubo.dynamo.spring.compiler.DynamoClassLoader;
import cn.wubo.dynamo.spring.exception.CompilationException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 1：动态编译 + 类加载 + 实例化 + 反射调用。
 */
@RestController
@RequestMapping("/api/compile")
public class CompileController {

    private final InstanceRegistry registry;

    public CompileController(InstanceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 编译源码 + 实例化。返回短 id + 类元数据 + 已注册实例列表。
     */
    @PostMapping("/load")
    public ResponseEntity<Map<String, Object>> load(@RequestBody LoadRequest req) {
        if (req == null || req.source == null || req.source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }
        DynamoClassLoader loader = DynamoClassLoader.create();
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
        Object instance;
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            instance = ctor.newInstance();
        } catch (NoSuchMethodException e) {
            loader.close();
            return ResponseEntity.status(422).body(Map.of(
                "error", "类必须有无参构造方法: " + clazz.getName()
            ));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            loader.close();
            return ResponseEntity.status(500).body(Map.of(
                "error", "实例化失败: " + e.getMessage()
            ));
        }
        String id = registry.putCompiled(instance, loader);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("className", clazz.getName());
        resp.put("methods", describeMethods(clazz));
        return ResponseEntity.ok(resp);
    }

    /**
     * 通过 id 调用已加载实例的方法。args 是 JSON 数组（元素是字符串，按方法形参类型转换）。
     */
    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody InvokeRequest req) {
        Object instance = registry.getCompiled(req.id);
        Method method = ArgsHelper.findMethod(instance.getClass(), req.methodName,
            (req.args == null ? 0 : req.args.size()));
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

    @GetMapping("/instances")
    public List<Map<String, Object>> instances() {
        return registry.listCompiled();
    }

    @DeleteMapping("/instances/{id}")
    public Map<String, Object> removeInstance(@PathVariable String id) {
        return Map.of("removed", registry.removeCompiled(id));
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

    // ---------- 请求 DTO ----------

    public static class LoadRequest {
        public String source;
    }

    public static class InvokeRequest {
        public String id;
        public String methodName;
        public List<?> args;
    }
}
