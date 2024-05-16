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

    private final Map<String, JavaMemClass> compiledClasses = new HashMap<>();

    protected MemFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    /**
     * 根据给定参数获取用于输出Java代码的JavaFileObject对象。
     * 这个方法在编译过程中被调用，用于生成和返回一个JavaFileObject实例，
     * 该实例代表了将要被输出的Java源代码或编译后的字节码文件。
     *
     * @param location  代码发生的位置。指定生成文件的位置。
     * @param className 类名。指定将要生成的文件所对应的类名。
     * @param kind      Java文件对象的类型。指定生成文件的类型（如源代码、字节码等）。
     * @param sibling   与新创建的JavaFileObject具有相同父级文件对象的兄弟文件对象。
     *                  可用于指定新文件在文件系统中的相对位置。
     * @return 用于输出Java代码的JavaFileObject对象。返回一个JavaMemClass实例，
     *         该实例用于存储将要输出的Java类的字节码。
     */
    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
        // 创建一个JavaMemClass实例，用于存储将要编译的类的字节码
        JavaMemClass javaMemClass = new JavaMemClass(className, kind);
        // 将新创建的JavaMemClass实例与对应的类名存储到compiledClasses中，
        // 以便于后续访问和使用
        compiledClasses.put(className, javaMemClass);
        return javaMemClass;
    }

    /**
     * 获取所有编译好的类的字节码数据
     *
     * @return 所有编译好的类的字节码数据的Map，键为类名，值为类的字节码数据
     */
    public Map<String, byte[]> getAllCompiledClassesData() {
        // 创建一个空的Map用于存储类的字节码数据
        Map<String, byte[]> classDataMap = new HashMap<>();
        // 遍历编译好的所有类，将每个类的字节码数据添加到Map中
        for (Map.Entry<String, JavaMemClass> entry : compiledClasses.entrySet()) {
            // 将每个编译好的类的字节码数据存入Map中
            classDataMap.put(entry.getKey(), entry.getValue().getBytes());
        }
        return classDataMap;
    }
}
