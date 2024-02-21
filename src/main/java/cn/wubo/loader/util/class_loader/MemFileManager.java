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
     *
     * @param location  代码发生的位置
     * @param className 类名
     * @param kind      Java文件对象的类型
     * @param sibling   与新创建的JavaFileObject具有相同父级文件对象的兄弟文件对象
     * @return 用于输出Java代码的JavaFileObject对象
     */
    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
        // 这里创建的JavaMemClass实例会被JavaCompiler用来存储编译好的类的字节码数据
        JavaMemClass javaMemClass = new JavaMemClass(className, kind);
        // 存储编译好的类
        compiledClasses.put(className, javaMemClass);
        return javaMemClass;
    }

    /**
     * 获取所有编译好的类的字节码数据
     *
     * @return 所有编译好的类的字节码数据的Map，键为类名，值为类的字节码数据
     */
    public Map<String, byte[]> getAllCompiledClassesData() {
        Map<String, byte[]> classDataMap = new HashMap<>();
        for (Map.Entry<String, JavaMemClass> entry : compiledClasses.entrySet()) {
            // 将每个编译好的类的字节码数据存入Map中
            classDataMap.put(entry.getKey(), entry.getValue().getBytes());
        }
        return classDataMap;
    }
}
