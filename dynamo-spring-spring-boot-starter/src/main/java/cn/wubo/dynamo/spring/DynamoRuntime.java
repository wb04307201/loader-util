package cn.wubo.dynamo.spring;

import cn.wubo.dynamo.spring.bean.DynamoBean;
import cn.wubo.dynamo.spring.compiler.CompilationResult;
import cn.wubo.dynamo.spring.compiler.CompilerOptions;
import cn.wubo.dynamo.spring.compiler.DynamoClassLoader;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 高级门面：把动态编译、类路径管理、Bean 注册粘合到一次生命周期内。
 *
 * <p>典型用法：
 * <pre>{@code
 * try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(beanFactory)) {
 *     Class<?> controller = runtime.compileAndLoad(source);
 *     runtime.registerController("dynamicCtrl", controller);
 * }
 * }</pre>
 *
 * <p>{@link #close()} 只释放 ClassLoader 字节码缓存；已注册的 Bean 定义保留——属于 Spring 容器生命周期。
 */
public final class DynamoRuntime implements AutoCloseable {

    private final DynamoClassLoader classLoader;
    private final DefaultListableBeanFactory beanFactory; // may be null

    private DynamoRuntime(DynamoClassLoader classLoader, DefaultListableBeanFactory beanFactory) {
        this.classLoader = classLoader;
        this.beanFactory = beanFactory;
    }

    public static DynamoRuntime create() {
        return new DynamoRuntime(DynamoClassLoader.create(), null);
    }

    public static DynamoRuntime create(ClassLoader parent) {
        return new DynamoRuntime(DynamoClassLoader.create(parent), null);
    }

    public static DynamoRuntime withBeanFactory(DefaultListableBeanFactory bf) {
        return new DynamoRuntime(DynamoClassLoader.create(), bf);
    }

    public static DynamoRuntime withBeanFactory(DefaultListableBeanFactory bf, ClassLoader parent) {
        return new DynamoRuntime(DynamoClassLoader.create(parent), bf);
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

    public DynamoClassLoader getClassLoader() {
        return classLoader;
    }

    public void addJarPath(String jarPath) {
        classLoader.addJarPath(jarPath);
    }

    // ---------- Bean 操作（仅 beanFactory 模式） ----------

    private DefaultListableBeanFactory requireBeanFactory() {
        if (beanFactory == null) {
            throw new IllegalStateException(
                "DynamoRuntime was created without a BeanFactory. " +
                "Use DynamoRuntime.withBeanFactory(...) to enable bean operations.");
        }
        return beanFactory;
    }

    public void registerBean(String beanName, Class<?> type) {
        DynamoBean.registerSingleton(requireBeanFactory(), beanName, type);
    }

    public void unregisterBean(String beanName) {
        DynamoBean.unregisterSingleton(requireBeanFactory(), beanName);
    }

    public void registerController(String beanName, Class<?> type) {
        DynamoBean.registerController(requireBeanFactory(), beanName, type);
    }

    public void unregisterController(String beanName) {
        DynamoBean.unregisterController(requireBeanFactory(), beanName);
    }

    public void refreshController(String beanName, Class<?> type) {
        DynamoBean.refreshController(requireBeanFactory(), beanName, type);
    }

    @Override
    public void close() {
        classLoader.close();
    }
}
