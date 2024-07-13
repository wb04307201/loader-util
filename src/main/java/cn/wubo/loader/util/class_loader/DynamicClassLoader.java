package cn.wubo.loader.util.class_loader;

import java.security.SecureClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * @description:动态编译加载器
 * @author: wubo
 * @date: 2022-11-21
 */
public class DynamicClassLoader extends SecureClassLoader {

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        JavaMemClass javaMemClass = MemFileManager.getJavaMemClass(fullClassName);
        if (javaMemClass == null) {
            throw new ClassNotFoundException("[动态编译]找不到类: " + fullClassName);
        }
        byte[] classData = javaMemClass.getBytes();
        return defineClass(fullClassName, classData, 0, classData.length);
    }

}
