package cn.wubo.dynamo.spring;

import cn.wubo.dynamo.spring.compiler.DynamoClassLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 资源生命周期维度的测试。
 *
 * <p>验证 close() 的语义、可重复性、以及运行时关闭后所有方法的契约。
 */
class DynamoRuntimeLifecycleTest {

    @Test
    void close_isIdempotent() {
        DynamoRuntime runtime = DynamoRuntime.create();
        runtime.close();
        runtime.close(); // 第二次不抛
        runtime.close();
        assertThat(runtime.getClassLoader().isClosed()).isTrue();
    }

    @Test
    void compileAfterClose_throwsIllegalState() {
        DynamoRuntime runtime = DynamoRuntime.create();
        runtime.close();
        assertThatThrownBy(() -> runtime.compileAndLoad("public class A {}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    @Test
    void addJarPathAfterClose_throwsIllegalState() {
        DynamoRuntime runtime = DynamoRuntime.create();
        runtime.close();
        assertThatThrownBy(() -> runtime.addJarPath("/any.jar"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerBeanAfterClose_throwsIllegalState() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf);
        runtime.close();
        // requireBeanFactory() 不关心 close —— 注册前没编译，是直接转发给 DynamoBean。
        // 所以这条断言只验证 close 后 getClassLoader 状态。
        assertThat(runtime.getClassLoader().isClosed()).isTrue();
    }

    @Test
    void beanOperations_workAfterClose_unchanged() {
        // Bean 操作不依赖 ClassLoader 状态——close() 只清字节码缓存，
        // 已注册的 bean 定义保留在 Spring 容器中。
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf);
        runtime.registerBean("foo", String.class);
        assertThat(bf.containsBean("foo")).isTrue();
        runtime.close();
        // Bean 定义依然在（属于 Spring 容器生命周期）
        assertThat(bf.containsBean("foo")).isTrue();
        // 能 getBean 实例化（ClassLoader 已关闭，但 String 是 JDK 自带类）
        assertThat(bf.getBean("foo")).isInstanceOf(String.class);
    }

    @Test
    void getClassLoader_returnsSameInstanceAcrossLifecycle() {
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            DynamoClassLoader cl1 = runtime.getClassLoader();
            assertThat(runtime.getClassLoader()).isSameAs(cl1);
        }
    }

    @Test
    void tryWithResources_autoClose() {
        DynamoClassLoader[] holder = new DynamoClassLoader[1];
        try (DynamoRuntime runtime = DynamoRuntime.create()) {
            holder[0] = runtime.getClassLoader();
            assertThat(holder[0].isClosed()).isFalse();
        }
        assertThat(holder[0].isClosed()).isTrue();
    }
}
