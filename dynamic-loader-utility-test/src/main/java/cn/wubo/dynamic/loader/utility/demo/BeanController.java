package cn.wubo.dynamic.loader.utility.demo;

import cn.wubo.dynamic.loader.utility.bean.DynamicBean;
import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;
import cn.wubo.dynamic.loader.utility.exception.CompilationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 3：直接通过 {@link DynamicBean#registerController} 注册 controller。
 *
 * <p>注册成功后立即从 {@link RequestMappingHandlerMapping#getHandlerMethods()} 反射
 * 读取路由列表返回给前端；注销后路由自动 404。
 */
@RestController
@RequestMapping("/api/bean")
public class BeanController {

    private final InstanceRegistry registry;
    private final ApplicationContext ctx;

    public BeanController(InstanceRegistry registry, ApplicationContext ctx) {
        this.registry = registry;
        this.ctx = ctx;
    }

    private DefaultListableBeanFactory bf() {
        return (DefaultListableBeanFactory) ((ConfigurableApplicationContext) ctx).getBeanFactory();
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (req.beanName == null || req.beanName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "beanName 不能为空"));
        }
        if (req.source == null || req.source.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 不能为空"));
        }

        // 用独立 loader 编译这个 controller 类
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

        try {
            DynamicBean.registerController(bf(), req.beanName, clazz);
        } catch (RuntimeException ex) {
            loader.close();
            return ResponseEntity.status(500).body(Map.of(
                "error", "注册 controller 失败: " + ex.getMessage()
            ));
        }
        registry.putDirectController(req.beanName, clazz);

        // 枚举本 bean 路由
        List<Map<String, Object>> routes = routesForBean(req.beanName);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("beanName", req.beanName);
        resp.put("className", clazz.getName());
        resp.put("routes", routes);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/unregister")
    public Map<String, Object> unregister(@RequestBody UnregisterRequest req) {
        if (!registry.listDirectControllers().containsKey(req.beanName)) {
            return Map.of("removed", false, "reason", "未在 demo 进程内动态注册过: " + req.beanName);
        }
        int routeCount = routesForBean(req.beanName).size();
        try {
            DynamicBean.unregisterController(bf(), req.beanName);
        } catch (RuntimeException ex) {
            return Map.of("removed", false, "error", ex.getMessage());
        }
        registry.removeDirectController(req.beanName);
        return Map.of("removed", true, "beanName", req.beanName, "routeCount", routeCount);
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Class<?>> e : registry.listDirectControllers().entrySet()) {
            out.add(Map.of(
                "beanName", e.getKey(),
                "className", e.getValue().getName(),
                "routes", routesForBean(e.getKey())
            ));
        }
        return Map.of("controllers", out);
    }

    // ---------- helpers ----------

    /**
     * 通过 {@link BeanControllerHelper#routesForBean} 反射列出指定 bean 的路由。
     */
    private List<Map<String, Object>> routesForBean(String beanName) {
        return BeanControllerHelper.routesForBean(ctx, beanName);
    }

    // ---------- DTO ----------

    public static class RegisterRequest {
        public String beanName;
        public String source;
    }

    public static class UnregisterRequest {
        public String beanName;
    }
}
