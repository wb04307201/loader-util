package cn.wubo.dynamic.loader.utility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicRuntimeTest {

    @Test
    void create_withoutBeanFactory_beanOpsThrow() {
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            assertThatThrownBy(() -> runtime.registerBean("x", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BeanFactory");
        }
    }

    @Test
    void close_releasesClassLoader() {
        DynamicRuntime runtime = DynamicRuntime.create();
        runtime.close();
        assertThat(runtime.getClassLoader().isClosed()).isTrue();
    }

    @Test
    void compileAndLoad_returnsClass() {
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            Class<?> clazz = runtime.compileAndLoad("public class RT { public int x() { return 7; } }");
            assertThat(clazz).isNotNull();
            assertThat(clazz.getSimpleName()).isEqualTo("RT");
        }
    }
}
