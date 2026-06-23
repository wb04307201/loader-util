package cn.wubo.dynamic.loader.utility.demo;

import cn.wubo.dynamic.loader.utility.DynamicRuntime;
import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 集中管理 demo 应用内所有动态状态。
 *
 * <p>demo 进程内 4 个 tab 共享同一进程，每个 tab 产生的实例 / 代理 / session 都
 * 用一个短 uuid（{@code uuid8}）作为 key 索引，存到对应 Map。删除时同步
 * 关闭 {@link DynamicClassLoader} 以释放字节码缓存。
 *
 * <p>{@link DynamicRuntime} 由 Spring 容器在进程关闭时统一释放；这里只在删除
 * session 时调一次 {@link DynamicRuntime#close()}，让用户感知"session close"。
 */
@Component
public class InstanceRegistry {

    // Tab 1：动态编译产生的实例 + 其专属 ClassLoader（每个 instance 一个 loader）
    private final Map<String, Object> compiledInstances = new ConcurrentHashMap<>();
    private final Map<String, DynamicClassLoader> compiledLoaders = new ConcurrentHashMap<>();

    // Tab 2：AOP 代理 + 关联 advice 日志队列 + ClassLoader（代理类要随目标类同 loader）
    private final Map<String, Object> aspectProxies = new ConcurrentHashMap<>();
    private final Map<String, Deque<Map<String, Object>>> aspectLogs = new ConcurrentHashMap<>();
    private final Map<String, DynamicClassLoader> aspectLoaders = new ConcurrentHashMap<>();

    // Tab 4：DynamicRuntime session；session 内 register 的 controller 名字集合（list 时使用）
    private final Map<String, DynamicRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runtimeControllers = new ConcurrentHashMap<>();
    // session 内的实例池（sessionId → instanceId → Object）
    private final Map<String, Map<String, Object>> runtimeInstances = new ConcurrentHashMap<>();
    // session 内的类池（sessionId → classId → Class）；compile 不自动 instantiate
    private final Map<String, Map<String, Class<?>>> runtimeClasses = new ConcurrentHashMap<>();

    // Tab 3：直接通过 DynamicBean.registerController 注册的 controller（name → Class）
    private final Map<String, Class<?>> directControllers = new ConcurrentHashMap<>();

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- Tab 1：compile ----------

    public String putCompiled(Object instance, DynamicClassLoader loader) {
        String id = newId();
        compiledInstances.put(id, instance);
        compiledLoaders.put(id, loader);
        return id;
    }

    public Object getCompiled(String id) {
        Object o = compiledInstances.get(id);
        if (o == null) {
            throw new IllegalArgumentException("Unknown compiled instance id: " + id);
        }
        return o;
    }

    public boolean removeCompiled(String id) {
        Object removed = compiledInstances.remove(id);
        DynamicClassLoader loader = compiledLoaders.remove(id);
        if (loader != null) {
            loader.close();
        }
        return removed != null;
    }

    public List<Map<String, Object>> listCompiled() {
        List<Map<String, Object>> out = new ArrayList<>();
        compiledInstances.forEach((id, inst) -> out.add(Map.of(
            "id", id,
            "className", inst.getClass().getName()
        )));
        Collections.sort(out, (a, b) -> ((String) a.get("id")).compareTo((String) b.get("id")));
        return out;
    }

    // ---------- Tab 2：aspect ----------

    public String putAspect(Object proxy, Deque<Map<String, Object>> log, DynamicClassLoader loader) {
        String id = newId();
        aspectProxies.put(id, proxy);
        aspectLogs.put(id, log);
        aspectLoaders.put(id, loader);
        return id;
    }

    public Object getAspect(String id) {
        Object o = aspectProxies.get(id);
        if (o == null) {
            throw new IllegalArgumentException("Unknown proxy id: " + id);
        }
        return o;
    }

    public Deque<Map<String, Object>> getAspectLog(String id) {
        Deque<Map<String, Object>> q = aspectLogs.get(id);
        if (q == null) {
            throw new IllegalArgumentException("Unknown proxy id: " + id);
        }
        return q;
    }

    public boolean removeAspect(String id) {
        boolean had = aspectProxies.remove(id) != null;
        aspectLogs.remove(id);
        DynamicClassLoader loader = aspectLoaders.remove(id);
        if (loader != null) {
            loader.close();
        }
        return had;
    }

    public List<Map<String, Object>> listAspect() {
        List<Map<String, Object>> out = new ArrayList<>();
        aspectProxies.forEach((id, proxy) -> {
            // 代理类继承自目标类，所以 getSuperclass 是真实类
            Class<?> target = proxy.getClass().getSuperclass();
            out.add(Map.of(
                "id", id,
                "className", target == null ? proxy.getClass().getName() : target.getName()
            ));
        });
        Collections.sort(out, (a, b) -> ((String) a.get("id")).compareTo((String) b.get("id")));
        return out;
    }

    // ---------- Tab 4：runtime session ----------

    public String putRuntime(DynamicRuntime runtime) {
        String id = newId();
        runtimes.put(id, runtime);
        runtimeControllers.put(id, ConcurrentHashMap.newKeySet());
        return id;
    }

    public DynamicRuntime getRuntime(String id) {
        DynamicRuntime r = runtimes.get(id);
        if (r == null) {
            throw new IllegalArgumentException("Unknown runtime session id: " + id);
        }
        return r;
    }

    public Set<String> getRuntimeControllers(String id) {
        return runtimeControllers.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    }

    public void trackRuntimeController(String id, String beanName) {
        getRuntimeControllers(id).add(beanName);
    }

    public void untrackRuntimeController(String id, String beanName) {
        Set<String> set = runtimeControllers.get(id);
        if (set != null) {
            set.remove(beanName);
        }
    }

    public boolean removeRuntime(String id) {
        DynamicRuntime r = runtimes.remove(id);
        runtimeControllers.remove(id);
        // 清掉 session 内所有实例
        Map<String, Object> pool = runtimeInstances.remove(id);
        if (pool != null) pool.clear();
        Map<String, Class<?>> classes = runtimeClasses.remove(id);
        if (classes != null) classes.clear();
        if (r != null) {
            r.close();
            return true;
        }
        return false;
    }

    public List<String> listRuntimes() {
        List<String> ids = new ArrayList<>(runtimes.keySet());
        Collections.sort(ids);
        return ids;
    }

    public String putRuntimeClass(String sessionId, Class<?> clazz) {
        String classId = newId();
        runtimeClasses
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(classId, clazz);
        return classId;
    }

    public Class<?> getRuntimeClass(String sessionId, String classId) {
        Map<String, Class<?>> pool = runtimeClasses.get(sessionId);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown runtime session id: " + sessionId);
        }
        Class<?> c = pool.get(classId);
        if (c == null) {
            throw new IllegalArgumentException(
                "Unknown class id '" + classId + "' in session " + sessionId);
        }
        return c;
    }

    public List<Map<String, Object>> listRuntimeClasses(String sessionId) {
        Map<String, Class<?>> pool = runtimeClasses.get(sessionId);
        if (pool == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        pool.forEach((cid, clazz) -> out.add(Map.of(
            "id", cid,
            "className", clazz.getName()
        )));
        Collections.sort(out, (a, b) -> ((String) a.get("id")).compareTo((String) b.get("id")));
        return out;
    }

    public String putRuntimeInstance(String sessionId, Object instance) {
        String instanceId = newId();
        runtimeInstances
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(instanceId, instance);
        return instanceId;
    }

    public Object getRuntimeInstance(String sessionId, String instanceId) {
        Map<String, Object> pool = runtimeInstances.get(sessionId);
        if (pool == null) {
            throw new IllegalArgumentException("Unknown runtime session id: " + sessionId);
        }
        Object o = pool.get(instanceId);
        if (o == null) {
            throw new IllegalArgumentException(
                "Unknown instance id '" + instanceId + "' in session " + sessionId);
        }
        return o;
    }

    public List<Map<String, Object>> listRuntimeInstances(String sessionId) {
        Map<String, Object> pool = runtimeInstances.get(sessionId);
        if (pool == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        pool.forEach((iid, inst) -> out.add(Map.of(
            "id", iid,
            "className", inst.getClass().getName()
        )));
        Collections.sort(out, (a, b) -> ((String) a.get("id")).compareTo((String) b.get("id")));
        return out;
    }

    // ---------- Tab 3：direct DynamicBean controller ----------

    public void putDirectController(String beanName, Class<?> type) {
        directControllers.put(beanName, type);
    }

    public void removeDirectController(String beanName) {
        directControllers.remove(beanName);
    }

    public Map<String, Class<?>> listDirectControllers() {
        return Map.copyOf(directControllers);
    }

    public Class<?> getDirectController(String beanName) {
        Class<?> c = directControllers.get(beanName);
        if (c == null) {
            throw new IllegalArgumentException("Unknown controller bean name: " + beanName);
        }
        return c;
    }

    // ---------- Default advice log factory ----------

    public static Deque<Map<String, Object>> newAdviceLog() {
        return new ConcurrentLinkedDeque<>();
    }
}
