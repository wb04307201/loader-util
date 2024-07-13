package cn.wubo.loader.util;

import cn.wubo.loader.util.class_loader.DynamicClassLoader;
import cn.wubo.loader.util.class_loader.JavaBuilder;
import cn.wubo.loader.util.class_loader.JavaMemClass;
import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@Slf4j
public class LoaderUtils {

    /**
     * 编译Java源代码并加载到内存中。
     *
     * @param javaSourceCode Java源代码字符串。
     * @param fullClassName  完全限定类名。
     */
    public static void compiler(String javaSourceCode, String fullClassName) {
        // 使用JavaBuilder编译Java源代码并获取内存中的类映射
        Map<String, JavaMemClass> javaMemClassMap = JavaBuilder.builder().compiler(javaSourceCode, fullClassName).getJavaMemClassMap();
        // 获取动态类加载器实例
        DynamicClassLoader classLoader = DynamicClassLoader.getInstance();
        // 将编译后的类信息添加到类加载器中
        for (Map.Entry<String, JavaMemClass> entry : javaMemClassMap.entrySet()) {
            classLoader.addClassMap(entry.getKey(), entry.getValue().getBytes());
        }
    }

    /**
     * 动态编译并加载Java源代码。
     * <p>
     * 该方法接收Java源代码和完整的类名作为参数，通过动态编译源代码并使用自定义类加载器加载生成的类，实现动态代码的执行。
     * 这种方式常用于运行时生成和执行代码，例如在某些框架中用于动态生成代理类。
     *
     * @param javaSourceCode Java源代码字符串。
     * @param fullClassName  完整的类名，包括包名。
     * @return 编译并加载后的类的Class对象。
     * @throws LoaderRuntimeException 如果编译或加载过程中发生错误，将抛出此运行时异常。
     */
    public static Class<?> compilerOnce(String javaSourceCode, String fullClassName) {
        // 使用JavaBuilder编译Java源代码，生成内存中的类对象。
        Map<String, JavaMemClass> javaMemClassMap = JavaBuilder.builder().compiler(javaSourceCode, fullClassName).getJavaMemClassMap();

        try (DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader())) {
            // 将内存中的类信息添加到自定义类加载器的类映射中。
            for (Map.Entry<String, JavaMemClass> entry : javaMemClassMap.entrySet()) {
                dynamicClassLoader.addClassMap(entry.getKey(), entry.getValue().getBytes());
            }
            // 使用自定义类加载器加载指定的类。
            return dynamicClassLoader.loadClass(fullClassName);
        } catch (IOException | ClassNotFoundException e) {
            // 抛出自定义异常，以更清晰地指示编译或加载过程中的错误。
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 通过类名加载类。
     *
     * @param fullClassName 完全限定类名。
     * @return 加载的类。
     */
    public static Class<?> load(String fullClassName) {
        try {
            // 获取动态类加载器实例并加载类
            DynamicClassLoader classLoader = DynamicClassLoader.getInstance();
            return classLoader.loadClass(fullClassName);
        } catch (ClassNotFoundException e) {
            // 抛出运行时异常，携带错误信息
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 将JAR路径添加到类路径中。
     *
     * @param jarPath JAR文件路径。
     */
    public static void addJarPath(String jarPath) {
        try {
            // 获取动态类加载器实例并添加JAR路径
            DynamicClassLoader.getInstance().addURL(new URL("jar:file:" + new File(jarPath).getAbsolutePath() + "!/"));
        } catch (MalformedURLException e) {
            // 抛出运行时异常，携带错误信息
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 注册单例 bean 到 Spring 应用上下文中。
     *
     * 此方法用于将指定的类作为单例 bean 注册到 Spring 应用上下文中。如果该类已经注册为 bean，则先销毁已存在的 bean 实例，然后重新注册。
     * 这确保了应用上下文中只有一个该类的实例存在。
     *
     * @param clazz 要注册为单例 bean 的类。
     * @return 注册后的 bean 名称。
     */
    public static String registerSingleton(Class<?> clazz) {
        // 通过类名生成 bean 名称。
        String beanName = SpringContextUtils.beanName(clazz.getName());
        // 检查是否已存在同名的 bean。如果存在，则先销毁该 bean。
        if (Boolean.TRUE.equals(SpringContextUtils.containsBean(beanName))) {
            SpringContextUtils.destroy(beanName);
        }
        // 注册指定类为单例 bean。
        SpringContextUtils.registerSingleton(beanName, clazz);
        // 返回注册后的 bean 名称。
        return beanName;
    }

    /**
     * 注册控制器类到Spring上下文中。
     *
     * 本方法用于将给定的控制器类注册到Spring应用程序上下文中。如果该类已经注册，则先注销原有的实例，再重新注册。
     * 这确保了Spring上下文中总是存在最新版本的控制器类实例。
     *
     * @param clazz 待注册的控制器类。此参数不应为null。
     * @return 注册后的bean名称。如果给定的类已经被注册，则返回更新后的bean名称。
     */
    public static String registerController(Class<?> clazz) {
        // 通过类名生成bean名称
        String beanName = SpringContextUtils.beanName(clazz.getName());
        // 检查是否已经存在同名的bean
        if (Boolean.TRUE.equals(SpringContextUtils.containsBean(beanName)))
            // 如果存在，则先注销原有的bean
            SpringContextUtils.unregisterController(beanName);
        // 注册新的控制器类
        SpringContextUtils.registerController(beanName, clazz);
        // 返回注册后的bean名称
        return beanName;
    }

    /**
     * 清理动态类加载器的缓存。
     *
     * 该方法调用了DynamicClassLoader的clear方法，旨在清理动态加载的类，以释放内存资源。
     * 当系统不再需要这些动态加载的类，或者需要重新加载类时，可以调用此方法。
     */
    public static void clear() {
        DynamicClassLoader.clear();
    }
}

