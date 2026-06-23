package cn.wubo.dynamic.loader.utility;

import cn.wubo.dynamic.loader.utility.bean.DynamicRequestMappingHandlerMapping;
import cn.wubo.dynamic.loader.utility.compiler.CompilerOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

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

    // ---------- Minor #1: 覆盖剩余 overloads ----------

    @Test
    void create_withExplicitParent_usesProvidedParent() {
        ClassLoader parent = new ClassLoader(DynamicRuntimeTest.class.getClassLoader()) {};
        try (DynamicRuntime runtime = DynamicRuntime.create(parent)) {
            assertThat(runtime.getClassLoader().getParent()).isSameAs(parent);
        }
    }

    @Test
    void withBeanFactory_acceptsExplicitParent() {
        ClassLoader parent = new ClassLoader(DynamicRuntimeTest.class.getClassLoader()) {};
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf, parent)) {
            assertThat(runtime.getClassLoader().getParent()).isSameAs(parent);
        }
    }

    @Test
    void compile_withOptions_passesThrough() {
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            assertThat(runtime.compile("public class Co { public int x() { return 1; } }",
                CompilerOptions.create().sourceVersion("17").targetVersion("17")).isSuccess()).isTrue();
        }
    }

    @Test
    void compileAndLoad_withOptions_passesThrough() throws Exception {
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            Class<?> clazz = runtime.compileAndLoad(
                "public class Clo { public String s() { return \"ok\"; } }",
                CompilerOptions.create().sourceVersion("17").targetVersion("17"));
            assertThat(clazz.getDeclaredConstructor().newInstance()
                .getClass().getDeclaredMethod("s").invoke(
                    clazz.getDeclaredConstructor().newInstance())).isEqualTo("ok");
        }
    }

    @Test
    void addJarPath_propagatesError() {
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            assertThatThrownBy(() -> runtime.addJarPath("/nonexistent.jar"))
                .hasMessageContaining("/nonexistent.jar");
        }
    }

    @Test
    void unregisterBean_removesFromFactory() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(Object.class);
        bf.registerBeanDefinition("marker", def);
        try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
            assertThat(bf.containsBean("marker")).isTrue();
            runtime.unregisterBean("marker");
            assertThat(bf.containsBean("marker")).isFalse();
        }
    }

    @Test
    void unregisterController_requiresMappingBean() {
        // 没注册 DynamicRequestMappingHandlerMapping → 必须给出明确错误
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(Object.class);
        bf.registerBeanDefinition("ctrl", def);
        try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
            assertThatThrownBy(() -> runtime.unregisterController("ctrl"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DynamicRequestMappingHandlerMapping");
        }
    }

    @Test
    void unregisterController_withMappingBean_removesMapping() {
        // DynamicRequestMappingHandlerMapping 继承 ApplicationObjectSupport，
        // 需要 ApplicationContext；用 GenericApplicationContext 提供，
        // 并显式注册 mapping bean（避免依赖 AutoConfiguration）。
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamicRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            GenericBeanDefinition ctrlDef = new GenericBeanDefinition();
            ctrlDef.setBeanClass(Object.class);
            bf.registerBeanDefinition("ctrl", ctrlDef);
            ctx.refresh();
            try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
                runtime.unregisterController("ctrl");
            }
        }
    }

    @Test
    void refreshController_requiresMappingBean() {
        // refreshController 复用 registerController，没注册 mapping 必抛。
        // 完整的 register→detect→mapping 路径由 DynamicBeanIT 覆盖。
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
            assertThatThrownBy(() -> runtime.refreshController("ctrl", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DynamicRequestMappingHandlerMapping");
        }
    }
}
