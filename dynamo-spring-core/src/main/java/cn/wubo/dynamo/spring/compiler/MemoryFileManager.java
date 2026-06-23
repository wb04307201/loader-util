package cn.wubo.dynamo.spring.compiler;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;

/**
 * 把编译输出的 CLASS kind 文件对象委托给 {@link MemoryClassFileObject}，其他 kind 透传给底层 file manager。
 */
public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private final DynamoClassLoader loader;

    protected MemoryFileManager(JavaFileManager fileManager, DynamoClassLoader loader) {
        super(fileManager);
        this.loader = loader;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String name,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            return new MemoryClassFileObject(name, loader);
        }
        return super.getJavaFileForOutput(location, name, kind, sibling);
    }
}
