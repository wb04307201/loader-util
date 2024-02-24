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

    /**
     * 添加类信息
     *
     * @param fullClassName 类的完全限定名
     * @param classData     类的数据
     */
    public void addClass(String fullClassName, byte[] classData) {
        classBytes.put(fullClassName, classData);
    }

    /**
     * 重写父类方法，用于查找指定的类。
     *
     * @param fullClassName 指定的类名
     * @return 查找到的类对象
     * @throws ClassNotFoundException 如果找不到指定的类，则抛出该异常
     */
    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        // 获取指定类的字节码数据
        byte[] classData = classBytes.get(fullClassName);
        if (classData == null) {
            // 如果找不到指定的类，则抛出ClassNotFoundException异常
            throw new ClassNotFoundException("[动态编译]找不到类: " + fullClassName);
        }
        // 定义并返回指定的类对象
        return defineClass(fullClassName, classData, 0, classData.length);
    }
}
