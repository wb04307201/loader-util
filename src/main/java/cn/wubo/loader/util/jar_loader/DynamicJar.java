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
public class DynamicJar {
    String filePath;

    public DynamicJar(String filePath) {
        this.filePath = filePath;
    }

    public static DynamicJar init(String filePath) {
        log.debug("初始化jar filePath:{}", filePath);
        return new DynamicJar(filePath);
    }

    public Class<?> load(String fullClassName) {
        try {
            URL url = new File(filePath).toURI().toURL();
            URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url});
            return urlClassLoader.loadClass(fullClassName);
        } catch (MalformedURLException | ClassNotFoundException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }
}
