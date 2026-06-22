package cn.wubo.dynamic.loader.utility.exception;

/**
 * 编译失败时抛出的运行时异常。携带结构化诊断信息。
 */
public class CompilationException extends RuntimeException {

    public CompilationException(String message) {
        super(message);
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}