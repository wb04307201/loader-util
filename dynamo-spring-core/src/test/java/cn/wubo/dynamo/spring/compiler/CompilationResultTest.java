package cn.wubo.dynamo.spring.compiler;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationResultTest {

    @Test
    void successResult_holdsClassAndClassName() {
        Class<?> clazz = String.class;
        CompilationResult result = new CompilationResult(true, "java.lang.String", clazz, List.of(), "");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getClassName()).isEqualTo("java.lang.String");
        assertThat(result.getCompiledClass()).isSameAs(clazz);
        assertThat(result.getDiagnostics()).isEmpty();
        assertThat(result.getErrorMessage()).isEmpty();
    }

    @Test
    void failureResult_holdsDiagnostics() {
        Diagnostic<? extends JavaFileObject> diag = new StubDiagnostic();
        CompilationResult result = new CompilationResult(false, "Foo", null, List.of(diag), "error: line 1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCompiledClass()).isNull();
        assertThat(result.getDiagnostics()).containsExactly(diag);
        assertThat(result.getErrorMessage()).isEqualTo("error: line 1");
    }

    @Test
    void nullDiagnostics_becomeEmptyList() {
        CompilationResult result = new CompilationResult(false, "Foo", null, null, "err");
        assertThat(result.getDiagnostics()).isEmpty();
    }

    @Test
    void nullErrorMessage_becomesEmptyString() {
        CompilationResult result = new CompilationResult(true, "Foo", null, List.of(), null);
        assertThat(result.getErrorMessage()).isEmpty();
    }

    @Test
    void diagnosticsList_isUnmodifiable() {
        CompilationResult result = new CompilationResult(false, "Foo", null, List.of(), "err");
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> result.getDiagnostics().add(new StubDiagnostic()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Minimal real Diagnostic for JDK 25 where mocking sealed JDK interfaces is restricted. */
    private static final class StubDiagnostic implements Diagnostic<JavaFileObject> {
        @Override public javax.tools.Diagnostic.Kind getKind() { return javax.tools.Diagnostic.Kind.ERROR; }
        @Override public JavaFileObject getSource() { return null; }
        @Override public long getPosition() { return 0; }
        @Override public long getStartPosition() { return 0; }
        @Override public long getEndPosition() { return 0; }
        @Override public long getLineNumber() { return 1; }
        @Override public long getColumnNumber() { return 1; }
        @Override public String getCode() { return "test"; }
        @Override public String getMessage(java.util.Locale locale) { return "stub diagnostic"; }
    }
}
