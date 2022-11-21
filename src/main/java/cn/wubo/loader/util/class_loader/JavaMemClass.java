package cn.wubo.loader.util.class_loader;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * @description: class保存对象(内存：不生成class文件)
 * @author: wubo
 * @date: 2022-11-21
 */
public class JavaMemClass extends SimpleJavaFileObject {

    protected final ByteArrayOutputStream classByteArrayOutputStream = new ByteArrayOutputStream();

    public JavaMemClass(String name, Kind kind) {
        super(URI.create("string:///" + name.replace('.', '/')
            + kind.extension), kind);
    }
 
    public byte[] getBytes() {
        return classByteArrayOutputStream.toByteArray();
    }
 
    @Override
    public OutputStream openOutputStream() {
        return classByteArrayOutputStream;
    }
    
}
