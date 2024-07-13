package cn.wubo.loader.util.class_loader;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;

import javax.tools.*;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class JavaBuilder {
    // Java编译器实例
    private JavaCompiler compiler;
    // 用于收集编译过程中的诊断信息
    private DiagnosticCollector<JavaFileObject> diagnosticCollector;
    // 编译结果输出的writer
    private Writer out;
    // 内存文件管理器，用于在内存中管理编译后的类文件
    private MemFileManager memFileManager;
    // 编译选项
    private Iterable<String> options;
    // 需要编译的类名集合
    private Iterable<String> classes;

    /**
     * JavaBuilder的构造函数。
     * 初始化Java编译器和相关组件。
     */
    public JavaBuilder() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.diagnosticCollector = new DiagnosticCollector<>();
        this.memFileManager = new MemFileManager(compiler.getStandardFileManager(diagnosticCollector, null, null));
    }

    /**
     * 静态方法，用于创建JavaBuilder实例。
     * 这是一种常见的Builder模式，用于实例化对象。
     *
     * @return JavaBuilder的实例
     */
    public static JavaBuilder builder() {
        return new JavaBuilder();
    }

    /**
     * 编译给定的Java源代码。
     *
     * @param javaSourceCode Java源代码字符串
     * @param fullClassName  完全限定类名
     * @return 当前JavaBuilder实例，支持方法链调用
     */
    public JavaBuilder compiler(String javaSourceCode, String fullClassName) {
        log.debug("开始执行编译");
        JavaMemSource file = new JavaMemSource(fullClassName, javaSourceCode);
        Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(file);
        log.debug("获取编译任务");
        JavaCompiler.CompilationTask task = compiler.getTask(out, memFileManager, diagnosticCollector, options, classes, compilationUnits);
        log.debug("执行编译");
        boolean result = task.call();
        if (!result) {
            String errorMessage = diagnosticCollector.getDiagnostics().stream().map(Object::toString).reduce("", (acc, x) -> acc + "\r\n" + x);
            log.debug("编译失败: {}", errorMessage);
            throw new LoaderRuntimeException("编译失败: " + errorMessage);
        }
        log.debug("编译成功");
        return this;
    }

    /**
     * 获取编译后的类的映射。
     * 这个映射将类名映射到内存中的类对象，可以用于后续的类加载和使用。
     *
     * @return 编译后的类的映射
     */
    public Map<String, JavaMemClass> getJavaMemClassMap() {
        return memFileManager.getJavaMemClassMap();
    }
}

