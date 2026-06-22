package cn.wubo.dynamic.loader.utility.compiler;

import cn.wubo.dynamic.loader.utility.exception.CompilationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicClassLoaderTest {

    @Test
    void compile_simpleClass_returnsSuccessResult() {
        try (DynamicClassLoader loader = DynamicClassLoader.create()) {
            String source = """
                public class Hello {
                    public String greet() { return "hi"; }
                }
                """;
            CompilationResult result = loader.compile(source);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getClassName()).isEqualTo("Hello");
            assertThat(result.getCompiledClass()).isNotNull();
            assertThat(result.getCompiledClass().getSimpleName()).isEqualTo("Hello");
        }
    }

    @Test
    void compileAndLoad_successful_returnsClass() throws Exception {
        try (DynamicClassLoader loader = DynamicClassLoader.create()) {
            String source = "public class A { public int x() { return 42; } }";
            Class<?> clazz = loader.compileAndLoad(source);
            assertThat(clazz).isNotNull();
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Object result = instance.getClass().getDeclaredMethod("x").invoke(instance);
            assertThat(result).isEqualTo(42);
        }
    }

    @Test
    void compileAndLoad_syntaxError_throwsCompilationException() {
        try (DynamicClassLoader loader = DynamicClassLoader.create()) {
            String bad = "public class { this is not valid }";
            assertThatThrownBy(() -> loader.compileAndLoad(bad))
                .isInstanceOf(CompilationException.class);
        }
    }

    @Test
    void compile_returnedResult_doesNotThrow() {
        try (DynamicClassLoader loader = DynamicClassLoader.create()) {
            String bad = "public class { this is not valid }";
            CompilationResult result = loader.compile(bad);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getDiagnostics()).isNotEmpty();
            assertThat(result.getCompiledClass()).isNull();
            assertThat(result.getErrorMessage()).isNotEmpty();
        }
    }

    @Test
    void create_withNullParent_usesContextClassLoader() {
        try (DynamicClassLoader loader = DynamicClassLoader.create()) {
            assertThat(loader.getParent()).isNotNull();
        }
    }

    @Test
    void close_isIdempotent() {
        DynamicClassLoader loader = DynamicClassLoader.create();
        loader.close();
        loader.close(); // 第二次不抛
    }

    @Test
    void compile_afterClose_throws() {
        DynamicClassLoader loader = DynamicClassLoader.create();
        loader.close();
        assertThatThrownBy(() -> loader.compile("public class A {}"))
            .isInstanceOf(IllegalStateException.class);
    }
}
