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
     * @param fullClassName 待加载的类名
     * @return 加载的类对象
     */
    public Class<?> load(String fullClassName) {
        try {
            URL url = new File(filePath).toURI().toURL();  // 将文件路径转换为URL格式
            try (URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url})) {
                return urlClassLoader.loadClass(fullClassName);  // 加载指定的类
            }  // 创建URLClassLoader
        } catch (ClassNotFoundException | IOException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);  // 抛出加载异常
        }
    }

}
