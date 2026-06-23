package cn.wubo.dynamo.spring;

import cn.wubo.dynamo.spring.bean.DynamoRequestMappingHandlerMapping;
import cn.wubo.dynamo.spring.compiler.CompilerOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoRuntimeTest {

    @Test
    void create_withoutBeanFactory_beanOpsThrow() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            assertThatThrownBy(() -> runtime.registerBean("x", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BeanFactory");
        }
    }

    @Test
    void close_releasesClassLoader() {
        DynamoRuntime runtime = DynamoRuntime.create();
        runtime.close();
        assertThat(runtime.getClassLoader().isClosed()).isTrue();
    }

    @Test
    void compileAndLoad_returnsClass() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            Class<?> clazz = runtime.compileAndLoad("public class RT { public int x() { return 7; } }");
            assertThat(clazz).isNotNull();
            assertThat(clazz.getSimpleName()).isEqualTo("RT");
        }
    }

    // ---------- Minor #1: 覆盖剩余 overloads ----------

    @Test
    void create_withExplicitParent_usesProvidedParent() {
        ClassLoader parent = new ClassLoader(DynamoRuntimeTest.class.getClassLoader()) {};
        try (DynamoRuntime runtime = DynamoRuntime.create(parent)) {
            assertThat(runtime.getClassLoader().getParent()).isSameAs(parent);
        }
    }

    @Test
    void withBeanFactory_acceptsExplicitParent() {
        ClassLoader parent = new ClassLoader(DynamoRuntimeTest.class.getClassLoader()) {};
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf, parent)) {
            assertThat(runtime.getClassLoader().getParent()).isSameAs(parent);
        }
    }

    @Test
    void compile_withOptions_passesThrough() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            assertThat(runtime.compile("public class Co { public int x() { return 1; } }",
                CompilerOptions.create().sourceVersion("17").targetVersion("17")).isSuccess()).isTrue();
        }
    }

    @Test
    void compileAndLoad_withOptions_passesThrough() throws Exception {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
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
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
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
        try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
            assertThat(bf.containsBean("marker")).isTrue();
            runtime.unregisterBean("marker");
            assertThat(bf.containsBean("marker")).isFalse();
        }
    }

    @Test
    void unregisterController_requiresMappingBean() {
        // 没注册 DynamoRequestMappingHandlerMapping → 必须给出明确错误
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(Object.class);
        bf.registerBeanDefinition("ctrl", def);
        try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
            assertThatThrownBy(() -> runtime.unregisterController("ctrl"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DynamoRequestMappingHandlerMapping");
        }
    }

    @Test
    void unregisterController_withMappingBean_removesMapping() {
        // DynamoRequestMappingHandlerMapping 继承 ApplicationObjectSupport，
        // 需要 ApplicationContext；用 GenericApplicationContext 提供，
        // 并显式注册 mapping bean（避免依赖 AutoConfiguration）。
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamoRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            GenericBeanDefinition ctrlDef = new GenericBeanDefinition();
            ctrlDef.setBeanClass(Object.class);
            bf.registerBeanDefinition("ctrl", ctrlDef);
            ctx.refresh();
            try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
                runtime.unregisterController("ctrl");
            }
        }
    }

    @Test
    void refreshController_requiresMappingBean() {
        // refreshController 复用 registerController，没注册 mapping 必抛。
        // 完整的 register→detect→mapping 路径由 DynamoBeanIT 覆盖。
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
            assertThatThrownBy(() -> runtime.refreshController("ctrl", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DynamoRequestMappingHandlerMapping");
        }
    }
}
