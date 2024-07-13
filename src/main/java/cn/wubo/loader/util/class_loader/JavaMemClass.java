package cn.wubo.loader.util.class_loader;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * JavaMemClass 是为了在内存中处理Java类文件而设计的。
 * 它继承自SimpleJavaFileObject，用于表示类文件的内容。
 */
public class JavaMemClass extends SimpleJavaFileObject {

    /**
     * 用于存储类文件字节码的 ByteArrayOutputStream。
     */
    protected final ByteArrayOutputStream classByteArrayOutputStream = new ByteArrayOutputStream();

    /**
     * 构造函数初始化JavaMemClass对象。
     *
     * @param name 类的全限定名，使用点（.）分隔。
     * @param kind 类文件的类型，例如SOURCE或CLASS。
     */
    public JavaMemClass(String name, Kind kind) {
        super(URI.create("string:///" + name.replace('.', '/')
                + kind.extension), kind);
    }

    /**
     * 获取类文件的字节码。
     *
     * @return 类文件的字节码数组。
     */
    public byte[] getBytes() {
        return classByteArrayOutputStream.toByteArray();
    }

    /**
     * 打开一个输出流，用于写入类文件的内容。
     *
     * @return 一个ByteArrayOutputStream，用于写入类文件的字节码。
     */
    @Override
    public OutputStream openOutputStream() {
        return classByteArrayOutputStream;
    }
}

