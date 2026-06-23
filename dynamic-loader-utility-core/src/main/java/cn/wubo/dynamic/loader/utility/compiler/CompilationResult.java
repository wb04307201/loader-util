package cn.wubo.dynamic.loader.utility.compiler;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collections;
import java.util.List;

/**
 * 不可变的编译结果。
 *
 * <p>无论编译成功或失败都会返回该对象——失败不抛异常（{@code compileAndLoad} 才抛）。
 * 这样调用方可以选择"快速失败"或"收集所有诊断信息后再处理"。
 */
public final class CompilationResult {

    private final boolean success;
    private final String className;
    private final Class<?> compiledClass;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    private final String errorMessage;

    public CompilationResult(boolean success,
                             String className,
                             Class<?> compiledClass,
                             List<Diagnostic<? extends JavaFileObject>> diagnostics,
                             String errorMessage) {
        this.success = success;
        this.className = className;
        this.compiledClass = compiledClass;
        this.diagnostics = diagnostics == null ? List.of() : Collections.unmodifiableList(diagnostics);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getClassName() {
        return className;
    }

    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
        return diagnostics;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
