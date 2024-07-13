package cn.wubo.loader.util.class_loader;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态类加载器，扩展自URLClassLoader，支持动态加载类。
 * 通过将类的字节码与类名映射，实现动态加载和查找类的功能。
 */
@Slf4j
public class DynamicClassLoader extends URLClassLoader {

    /**
     * 类映射，存储类的完全限定名和对应的Class对象。
     */
    private Map<String, Class<?>> classMap = new HashMap<>();

    /**
     * 构造函数，使用当前线程的上下文类加载器作为父类加载器。
     *
     * @param parent 父类加载器
     */
    public DynamicClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    /**
     * 单例模式，获取动态类加载器的实例。
     */
    @Getter
    private static DynamicClassLoader instance = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());

    /**
     * 清理并重新初始化动态类加载器。
     * 该方法旨在重新配置动态类加载器，以便它可以继续加载新的类，而不会受到之前加载类的影响。
     * 这是通过关闭当前的动态类加载器实例并创建一个新的实例来实现的。
     * 在这个过程中，它使用当前线程的上下文类加载器作为新实例的基础。
     * 如果在关闭当前实例或创建新实例的过程中发生IO异常，将会抛出一个LoaderRuntimeException。
     */
    public static void clear() {
        try {
            // 关闭当前的动态类加载器实例
            instance.close();
            // 创建一个新的动态类加载器实例，基于当前线程的上下文类加载器
            instance = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        } catch (IOException e) {
            // 抛出运行时异常，带有IO异常的信息和根异常
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 添加URL到类加载器的搜索路径。
     *
     * @param url 要添加的URL
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * 将类的字节码添加到类映射中，以供动态加载。
     *
     * @param fullClassName 类的完全限定名
     * @param classData     类的字节码数据
     */
    public void addClassMap(String fullClassName, byte[] classData) {
        classMap.put(fullClassName, defineClass(fullClassName, classData, 0, classData.length));
    }

    /**
     * 根据类的完全限定名查找并加载类。
     * 首先尝试从类映射中加载类，如果未找到，则调用父类加载器的findClass方法。
     *
     * @param fullClassName 类的完全限定名
     * @return 加载的Class对象
     * @throws ClassNotFoundException 如果类未找到
     */
    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        // 尝试从类映射中加载类
        Class<?> clazz = classMap.get(fullClassName);
        // 如果类在映射中不存在，则记录debug信息
        if (clazz == null) log.debug("[动态编译]classMap未找到类:" + fullClassName);
        else return clazz; // 如果类在classMap中找到，则直接返回
        // 如果classMap中未找到类，则调用父ClassLoader的findClass方法尝试加载类
        return super.findClass(fullClassName);
    }
}

