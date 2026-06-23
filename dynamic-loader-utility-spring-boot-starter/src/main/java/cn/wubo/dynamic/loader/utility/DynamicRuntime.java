package cn.wubo.dynamic.loader.utility;

import cn.wubo.dynamic.loader.utility.bean.DynamicBean;
import cn.wubo.dynamic.loader.utility.compiler.CompilationResult;
import cn.wubo.dynamic.loader.utility.compiler.CompilerOptions;
import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 高级门面：把动态编译、类路径管理、Bean 注册粘合到一次生命周期内。
 *
 * <p>典型用法：
 * <pre>{@code
 * try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(beanFactory)) {
 *     Class<?> controller = runtime.compileAndLoad(source);
 *     runtime.registerController("dynamicCtrl", controller);
 * }
 * }</pre>
 *
 * <p>{@link #close()} 只释放 ClassLoader 字节码缓存；已注册的 Bean 定义保留——属于 Spring 容器生命周期。
 */
public final class DynamicRuntime implements AutoCloseable {

    private final DynamicClassLoader classLoader;
    private final DefaultListableBeanFactory beanFactory; // may be null

    private DynamicRuntime(DynamicClassLoader classLoader, DefaultListableBeanFactory beanFactory) {
        this.classLoader = classLoader;
        this.beanFactory = beanFactory;
    }

    public static DynamicRuntime create() {
        return new DynamicRuntime(DynamicClassLoader.create(), null);
    }

    public static DynamicRuntime create(ClassLoader parent) {
        return new DynamicRuntime(DynamicClassLoader.create(parent), null);
    }

    public static DynamicRuntime withBeanFactory(DefaultListableBeanFactory bf) {
        return new DynamicRuntime(DynamicClassLoader.create(), bf);
    }

    public static DynamicRuntime withBeanFactory(DefaultListableBeanFactory bf, ClassLoader parent) {
        return new DynamicRuntime(DynamicClassLoader.create(parent), bf);
    }

    // ---------- 编译 ----------

    public CompilationResult compile(String sourceCode) {
        return classLoader.compile(sourceCode);
    }

    public CompilationResult compile(String sourceCode, CompilerOptions opts) {
        return classLoader.compile(sourceCode, opts);
    }

    public Class<?> compileAndLoad(String sourceCode) {
        return classLoader.compileAndLoad(sourceCode);
    }

    public Class<?> compileAndLoad(String sourceCode, CompilerOptions opts) {
        return classLoader.compileAndLoad(sourceCode, opts);
    }

    public DynamicClassLoader getClassLoader() {
        return classLoader;
    }

    public void addJarPath(String jarPath) {
        classLoader.addJarPath(jarPath);
    }

    // ---------- Bean 操作（仅 beanFactory 模式） ----------

    private DefaultListableBeanFactory requireBeanFactory() {
        if (beanFactory == null) {
            throw new IllegalStateException(
                "DynamicRuntime was created without a BeanFactory. " +
                "Use DynamicRuntime.withBeanFactory(...) to enable bean operations.");
        }
        return beanFactory;
    }

    public void registerBean(String beanName, Class<?> type) {
        DynamicBean.registerSingleton(requireBeanFactory(), beanName, type);
    }

    public void unregisterBean(String beanName) {
        DynamicBean.unregisterSingleton(requireBeanFactory(), beanName);
    }

    public void registerController(String beanName, Class<?> type) {
        DynamicBean.registerController(requireBeanFactory(), beanName, type);
    }

    public void unregisterController(String beanName) {
        DynamicBean.unregisterController(requireBeanFactory(), beanName);
    }

    public void refreshController(String beanName, Class<?> type) {
        DynamicBean.refreshController(requireBeanFactory(), beanName, type);
    }

    @Override
    public void close() {
        classLoader.close();
    }
}
