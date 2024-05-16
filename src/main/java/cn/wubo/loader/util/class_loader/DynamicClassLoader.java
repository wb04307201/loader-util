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
     * 该方法用于添加一个类的完全限定名及其对应的数据到类信息存储结构中。
     *
     * @param fullClassName 类的完全限定名，指明了类在项目中的唯一标识。
     * @param classData     类的数据，以字节数组的形式表示，包含了类的二进制信息。
     */
    public void addClass(String fullClassName, byte[] classData) {
        // 将类的完全限定名和对应的字节数组存储到classBytes中
        classBytes.put(fullClassName, classData);
    }

    /**
     * 重写父类方法，用于查找指定的类。
     * 这个方法首先尝试从一个映射（classBytes）中获取指定类名的字节码数据。
     * 如果找到了字节码数据，则使用这些数据定义并返回一个类对象。
     * 如果没有找到相应的字节码数据，则抛出ClassNotFoundException。
     *
     * @param fullClassName 指定的类名，包括包名。
     * @return 查找到的类对象。
     * @throws ClassNotFoundException 如果找不到指定的类，则抛出该异常。
     */
    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        // 尝试从classBytes映射中获取指定类的字节码数据
        byte[] classData = classBytes.get(fullClassName);
        if (classData == null) {
            // 如果未能找到类的字节码，则抛出异常
            throw new ClassNotFoundException("[动态编译]找不到类: " + fullClassName);
        }
        // 定义并返回一个类对象，使用找到的字节码数据
        return defineClass(fullClassName, classData, 0, classData.length);
    }

}
