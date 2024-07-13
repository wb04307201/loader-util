package cn.wubo.loader.util.class_loader;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * @description: class保存对象(内存 ： 不生成class文件)
 * @author: wubo
 * @date: 2022-11-21
 */
public class JavaMemClass extends SimpleJavaFileObject {

    protected final ByteArrayOutputStream classByteArrayOutputStream = new ByteArrayOutputStream();

    public JavaMemClass(String name, Kind kind) {
        super(URI.create("string:///" + name.replace('.', '/')
                + kind.extension), kind);
    }

    /**
     * 获取字节数组
     * <p>
     * 该方法不需要接受任何参数，它将返回一个字节数组。
     * 主要用于将内部缓存的字节信息转换为字节数组输出。
     *
     * @return byte[] 返回一个包含字节信息的数组
     */
    public byte[] getBytes() {
        // 将内部存储的字节流转换为字节数组并返回
        return classByteArrayOutputStream.toByteArray();
    }

    /**
     * 重写openOutputStream方法
     * 这个方法重写了父类中的openOutputStream方法，目的是提供一个特定的输出流返回。
     *
     * @return 返回classByteArrayOutputStream对象 - 这是一个OutputStream的实例，
     * 用于允许外部写入数据到类的内部缓存中。
     */
    @Override
    public OutputStream openOutputStream() {
        return classByteArrayOutputStream;
    }
}
