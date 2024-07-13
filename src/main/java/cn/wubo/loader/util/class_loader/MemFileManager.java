package cn.wubo.loader.util.class_loader;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.Map;

/**
 * @description: 内存文件管理器
 * @author: wubo
 * @date: 2022-11-21
 */
public class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private static Map<String, JavaMemClass> compiledClasses = new HashMap<>();

    protected MemFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
        JavaMemClass javaMemClass = new JavaMemClass(className, kind);
        compiledClasses.put(className, javaMemClass);
        return javaMemClass;
    }

    public static JavaMemClass getJavaMemClass(String className) {
        return compiledClasses.get(className);
    }
}
