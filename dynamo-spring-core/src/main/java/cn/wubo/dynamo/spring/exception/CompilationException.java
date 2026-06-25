package cn.wubo.dynamo.spring.exception;

import cn.wubo.dynamo.spring.compiler.CompilationResult;

/**
 * 编译失败时抛出的运行时异常。携带结构化诊断信息。
 */
public class CompilationException extends RuntimeException {

    private final CompilationResult result;

    public CompilationException(String message) {
        super(message);
        this.result = null;
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
        this.result = null;
    }

    public CompilationException(String message, CompilationResult result) {
        super(message);
        this.result = result;
    }

    public CompilationException(String message, CompilationResult result, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    /**
     * @return the {@link CompilationResult} associated with this failure, or {@code null}
     *         if this exception was constructed without one.
     */
    public CompilationResult getResult() {
        return result;
    }
}