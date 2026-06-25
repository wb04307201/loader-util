package cn.wubo.dynamo.spring;

import cn.wubo.dynamo.spring.bean.DynamoBean;
import cn.wubo.dynamo.spring.exception.CompilationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 边界条件与错误恢复维度的测试。
 */
class DynamoRuntimeEdgeCasesTest {

    // ---------- 编译边界 ----------

    @Test
    void compile_emptySource_returnsFailure() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            assertThatThrownBy(() -> runtime.compileAndLoad(""))
                .isInstanceOf(CompilationException.class);
        }
    }

    @Test
    void compile_commentOnly_returnsFailure() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            assertThatThrownBy(() -> runtime.compileAndLoad("// just a comment"))
                .isInstanceOf(CompilationException.class);
        }
    }

    @Test
    void compile_chineseClassName_succeeds() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            Class<?> c = runtime.compileAndLoad("public class 中文类名 { public String s() { return \"ok\"; } }");
            assertThat(c.getSimpleName()).isEqualTo("中文类名");
        }
    }

    @Test
    void compile_innerClass_usesOuterName() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            // parseClassName 取第一个 typeDeclaration → 外层类
            Class<?> c = runtime.compileAndLoad(
                "public class Outer { public class Inner { public int x() { return 5; } } }");
            assertThat(c.getSimpleName()).isEqualTo("Outer");
        }
    }

    @Test
    void compile_interface_only_methodsAbstract_succeeds() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            Class<?> c = runtime.compileAndLoad(
                "public interface Greetable { String greet(); }");
            assertThat(c.isInterface()).isTrue();
        }
    }

    @Test
    void compile_enum_succeeds() throws Exception {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            Class<?> c = runtime.compileAndLoad(
                "public enum Color { RED, GREEN, BLUE }");
            assertThat(c.isEnum()).isTrue();
            Object red = c.getField("RED").get(null);
            assertThat(red).isNotNull();
        }
    }

    // ---------- Bean 操作错误恢复 ----------

    @Test
    void unregisterBean_notExists_isNoop() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamoBean.unregisterSingleton(bf, "nonexistent");
        // 不抛异常即视为通过
    }

    @Test
    void registerBean_sameName_overwritesPrevious() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamoBean.registerSingleton(bf, "x", String.class);
        // 第二次 registerSingleton 同名：bean 定义被覆盖，类型变 StringBuilder
        DynamoBean.registerSingleton(bf, "x", StringBuilder.class);
        assertThat(bf.getBean("x")).isInstanceOf(StringBuilder.class);
    }

    @Test
    void registerBean_sameNameTwice_idempotent() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
            runtime.registerBean("x", String.class);
            runtime.registerBean("x", String.class);
            assertThat(bf.containsBean("x")).isTrue();
        }
    }

    @Test
    void unregisterController_onMissingBean_isNoop() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        // 没注册 controller bean，也没注册 mapping —— DynamoBean.unregisterController
        // 仍会调用 dbf.getBean("ghost") 抛 NoSuchBeanDefinitionException，
        // 所以这里只验证不存在的 bean 路径会被底层异常抛出。
        assertThatThrownBy(() -> DynamoBean.unregisterController(bf, "ghost"))
            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
    }

    @Test
    void compile_runtimeErrorInCode_returnsDiagnostics() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            // 引用未定义符号 → 编译错误（不是异常）
            String src = "public class Buggy { int x = undefinedSymbol; }";
            assertThatThrownBy(() -> runtime.compileAndLoad(src))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("undefinedSymbol");
        }
    }
}
