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

    /**
     * 编译的时候返回的class字节数组-支持内部类
     */
    private final Map<String, byte[]> classBytes = new HashMap<>();

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void addClass(String fullClassName, byte[] classData) {
        classBytes.put(fullClassName, classData);
    }

    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        byte[] classData = classBytes.get(fullClassName);
        if (classData == null) {
            throw new ClassNotFoundException("[动态编译]找不到类: " + fullClassName);
        }
        return defineClass(fullClassName, classData, 0, classData.length);
    }
}
