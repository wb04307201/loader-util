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
        // 1.编译的class字节数组为null，则说明已经编译过了，是后续的调用，不用编译
        if (classData == null || classData.length == 0) {
            throw new ClassNotFoundException("[动态编译]classdata不存在");
        }
        // 2.加载
        return defineClass(fullClassName, classData, 0, classData.length);
    }



}
