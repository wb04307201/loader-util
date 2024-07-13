package cn.wubo.loader.util.class_loader;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.Map;

/**
 * 在内存中管理编译后Java类的文件管理器。
 * 该类通过继承ForwardingJavaFileManager，实现了一个内存中的Java类管理机制，
 * 主要用于在不将类文件写入磁盘的情况下，存储和管理编译后的Java类。
 */
public class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    /**
     * 用于存储编译后的Java类的内存映射。
     * 键为类名，值为JavaMemClass对象，后者包含类的字节码和其他元数据。
     */
    private Map<String, JavaMemClass> javaMemClassMap = new HashMap<>();

    /**
     * 构造函数，初始化MemFileManager。
     *
     * @param fileManager 基础文件管理器，通常是一个与文件系统交互的文件管理器。
     */
    protected MemFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    /**
     * 重写getJavaFileForOutput方法，用于在内存中创建并返回一个新的JavaFileObject。
     * 这个方法是编译器在需要写入.class文件时调用的，我们在这里拦截这个调用，
     * 并将.class文件的内容存储在JavaMemClass对象中，而不是写入磁盘。
     *
     * @param location 位置标识，用于指示.class文件应该放置的位置，这里忽略，因为我们在内存中管理类。
     * @param className 类的全限定名。
     * @param kind 类文件的类型，例如SOURCE、CLASS等。
     * @param sibling 如果存在，表示与新类文件相邻的现有文件对象。
     * @return JavaMemClass对象，它在内存中代表了.class文件。
     */
    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
        JavaMemClass javaMemClass = new JavaMemClass(className, kind);
        javaMemClassMap.put(className, javaMemClass);
        return javaMemClass;
    }

    /**
     * 提供对javaMemClassMap的访问，允许外部获取和检查编译后的类。
     *
     * @return 当前内存中所有编译后类的映射。
     */
    public Map<String, JavaMemClass> getJavaMemClassMap() {
        return javaMemClassMap;
    }
}

