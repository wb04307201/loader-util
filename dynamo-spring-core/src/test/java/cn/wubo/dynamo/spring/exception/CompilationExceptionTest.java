package cn.wubo.dynamo.spring.exception;

import cn.wubo.dynamo.spring.compiler.CompilationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationExceptionTest {

    @Test
    void ctor_messageOnly_resultIsNull() {
        CompilationException ex = new CompilationException("boom");
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getResult()).isNull();
    }

    @Test
    void ctor_messageAndCause_resultIsNull() {
        Throwable cause = new IllegalStateException("inner");
        CompilationException ex = new CompilationException("boom", cause);
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getResult()).isNull();
    }

    @Test
    void ctor_messageAndResult_resultAttached() {
        CompilationResult result = new CompilationResult(false, "X", null, java.util.List.of(), "err");
        CompilationException ex = new CompilationException("boom", result);
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getResult()).isSameAs(result);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void ctor_messageResultAndCause_allAttached() {
        CompilationResult result = new CompilationResult(false, "X", null, java.util.List.of(), "err");
        Throwable cause = new IllegalStateException("inner");
        CompilationException ex = new CompilationException("boom", result, cause);
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getResult()).isSameAs(result);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
