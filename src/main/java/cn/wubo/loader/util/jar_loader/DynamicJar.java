package cn.wubo.loader.util.jar_loader;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 将外部jar读入内存中
 */
@Slf4j
public class DynamicJar extends URLClassLoader {

    public DynamicJar(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * 初始化动态Jar实例。
     * 该方法用于创建一个DynamicJar对象，该对象封装了对Jar文件的访问，允许在运行时动态加载和使用Jar文件中的类和资源。
     *
     * @param filePath Jar文件的路径。这是一个字符串参数，指定Jar文件在文件系统中的位置。
     * @return 返回一个DynamicJar实例，该实例封装了指定Jar文件的访问。
     * @throws LoaderRuntimeException 如果文件路径不正确，导致URL转换失败，将抛出此运行时异常。
     */
    public static DynamicJar init(String filePath) {
        // 日志记录jar文件路径初始化信息
        log.debug("jar文件路径初始化:{}", filePath);
        try {
            // 将文件路径转换为URL，以便于后续的加载操作
            URL url = new File(filePath).toURI().toURL();
            // 使用当前线程的上下文类加载器和转换后的URL创建DynamicJar实例
            return new DynamicJar(new URL[]{url}, Thread.currentThread().getContextClassLoader());
        } catch (MalformedURLException e) {
            // 如果文件路径不是有效的URL，抛出自定义运行时异常
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 根据全类名加载类。
     *
     * 本方法尝试加载指定全类名的类。如果类加载失败，将抛出一个自定义的运行时异常。
     * 这种方式的类加载有利于在运行时动态地处理类，为系统增加了灵活性。
     *
     * @param fullClassName 待加载类的全类名，包括包名。
     * @return 成功加载的类的Class对象。
     * @throws LoaderRuntimeException 如果类找不到，将抛出此运行时异常，包含详细的错误信息。
     */
    public Class<?> load(String fullClassName) {
        try {
            // 尝试加载指定的类。
            return loadClass(fullClassName);
        } catch (ClassNotFoundException e) {
            // 类加载失败时，抛出自定义的运行时异常。
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }
}
