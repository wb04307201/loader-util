package cn.wubo.loader.util.jar_loader;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    public void load(String filePath) {
        try {
            URLClassLoader classLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            URL url = new File(filePath).toURI().toURL();
            addURL.setAccessible(true);
            addURL.invoke(classLoader, url);
        } catch (NoSuchMethodException | MalformedURLException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
