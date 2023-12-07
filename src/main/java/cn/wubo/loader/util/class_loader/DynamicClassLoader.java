package cn.wubo.loader.util.class_loader;

import java.security.SecureClassLoader;

/**
 * @description:动态编译加载器
 * @author: wubo
 * @date: 2022-11-21
 */
public class DynamicClassLoader extends SecureClassLoader {

    /**
     * 编译的时候返回的class字节数组
     */
    private byte[] classData;

    public DynamicClassLoader(byte[] classData) {
        super();
        this.classData = classData;
    }

    @Override
    protected Class<?> findClass(String fullClassName) throws ClassNotFoundException {
        // 1. 判断编译的class字节数组为null，若为null则说明已经编译过，无需再编译
        if (classData == null || classData.length == 0)
            throw new ClassNotFoundException("[动态编译]classdata不存在");
        // 2. 加载class字节数组
        return defineClass(fullClassName, classData, 0, classData.length);
    }




}
