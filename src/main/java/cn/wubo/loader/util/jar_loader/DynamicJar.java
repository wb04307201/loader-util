package cn.wubo.loader.util.jar_loader;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 将外部jar读入内存中
 */
@Slf4j
public class DynamicJar {
    String filePath;

    public DynamicJar(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 初始化一个DynamicJar对象
     *
     * @param filePath 文件路径
     * @return DynamicJar对象
     */
    public static DynamicJar init(String filePath) {
        log.debug("jar文件路径初始化:{}", filePath);
        return new DynamicJar(filePath);
    }

    /**
     * 加载指定的类名。
     *
     * @param fullClassName 待加载的类的完整名称，包括包名。
     * @return 加载的类对象。如果指定的类成功加载，则返回对应的Class对象。
     * @throws LoaderRuntimeException 如果类加载失败，将抛出此运行时异常。
     */
    public Class<?> load(String fullClassName) {
        try {
            // 将文件路径转换为URL格式，为创建URLClassLoader做准备
            URL url = new File(filePath).toURI().toURL();
            try (URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url})) {
                // 使用URLClassLoader加载指定的类
                return urlClassLoader.loadClass(fullClassName);
            }  // URLClassLoader的资源被自动关闭
        } catch (ClassNotFoundException | IOException e) {
            // 当类找不到或发生IO异常时，抛出自定义的加载异常
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }
}
