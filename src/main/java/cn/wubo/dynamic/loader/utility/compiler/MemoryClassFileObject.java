package cn.wubo.dynamic.loader.utility.compiler;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/**
 * 内存中的类字节码文件对象。{@code javac} 写入字节码时，会通过 {@link #openOutputStream}
 * 返回的流把字节码喂回来；本类在 close 时回调所属的 {@link DynamicClassLoader} 把字节码存起来。
 */
public class MemoryClassFileObject extends SimpleJavaFileObject {

    private final String name;
    private final DynamicClassLoader loader;

    public MemoryClassFileObject(String name, DynamicClassLoader loader) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.CLASS.extension),
              Kind.CLASS);
        this.name = name;
        this.loader = loader;
    }

    @Override
    public OutputStream openOutputStream() {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                loader.registerCompiledClass(name, this.toByteArray());
            }
        };
    }
}
