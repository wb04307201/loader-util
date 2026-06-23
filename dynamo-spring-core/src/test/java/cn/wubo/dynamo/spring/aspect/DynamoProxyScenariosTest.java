package cn.wubo.dynamo.spring.aspect;

import cn.wubo.dynamo.spring.exception.ProxyCreationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DynamoProxy 多场景切面行为测试。
 *
 * <p>验证代理在不同情况下的正确性：
 * <ul>
 *   <li>方法抛异常时 afterThrow 触发、after 不触发
 *   <li>多次调用同一代理方法，advice 状态隔离（ThreadLocal-based SimpleAdvice）
 *   <li>自定义 advice 能拿到 target/method/args/result
 *   <li>proxy(target) 和 proxy(type) 行为一致
 *   <li>无法代理没有无参构造的类
 *   <li>代理类与原类不同 Class 对象
 * </ul>
 */
class DynamoProxyScenariosTest {

    /** 测试用 service。 */
    public static class Calc {
        public int add(int a, int b) { return a + b; }
        public int divide(int a, int b) {
            if (b == 0) throw new ArithmeticException("/ by zero");
            return a / b;
        }
        public String greet(String name) { return "hello " + name; }
    }

    public static class NoCtor {
        public NoCtor(String s) {}
        public int x() { return 42; }
    }

    /** 录制 advice 触发的顺序与参数。 */
    static class RecordingAdvice implements IAdvice {
        final List<String> events = new ArrayList<>();
        @Override public void before(Object target, Method method, Object[] args) {
            events.add("before:" + method.getName());
        }
        @Override public void after(Object target, Method method, Object[] args, Object result) {
            events.add("after:" + method.getName() + "=" + result);
        }
        @Override public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
            events.add("afterThrow:" + method.getName() + ":" + cause.getClass().getSimpleName());
        }
    }

    @Test
    void proxy_methodInvokes_adviceSeesResult() {
        Calc target = new Calc();
        RecordingAdvice advice = new RecordingAdvice();
        Calc proxy = DynamoProxy.proxy(target, advice);
        int r = proxy.add(2, 3);
        assertThat(r).isEqualTo(5);
        assertThat(advice.events).containsExactly("before:add", "after:add=5");
    }

    @Test
    void proxy_methodThrows_afterThrowTriggered() {
        Calc target = new Calc();
        RecordingAdvice advice = new RecordingAdvice();
        Calc proxy = DynamoProxy.proxy(target, advice);
        assertThatThrownBy(() -> proxy.divide(1, 0))
            .isInstanceOf(ArithmeticException.class);
        assertThat(advice.events).hasSize(2);
        assertThat(advice.events.get(0)).isEqualTo("before:divide");
        assertThat(advice.events.get(1)).startsWith("afterThrow:divide:ArithmeticException");
    }

    @Test
    void proxy_consecutiveCalls_adviceIndependent() {
        Calc target = new Calc();
        RecordingAdvice advice = new RecordingAdvice();
        Calc proxy = DynamoProxy.proxy(target, advice);
        proxy.add(1, 1);
        proxy.add(2, 2);
        proxy.greet("x");
        // 6 个事件：3×(before+after)
        assertThat(advice.events).hasSize(6);
        assertThat(advice.events.get(0)).isEqualTo("before:add");
        assertThat(advice.events.get(1)).isEqualTo("after:add=2");
        assertThat(advice.events.get(4)).isEqualTo("before:greet");
        assertThat(advice.events.get(5)).isEqualTo("after:greet=hello x");
    }

    @Test
    void proxy_withExplicitType_matchesInstanceOverload() {
        Calc target = new Calc();
        RecordingAdvice advice = new RecordingAdvice();
        Calc viaInstance = DynamoProxy.proxy(target, advice);
        Calc viaType = DynamoProxy.proxy(Calc.class, advice);
        assertThat(viaInstance.add(1, 2)).isEqualTo(3);
        assertThat(viaType.add(4, 5)).isEqualTo(9);
    }

    @Test
    void proxy_classDiffersFromTarget() {
        Calc target = new Calc();
        RecordingAdvice advice = new RecordingAdvice();
        Calc proxy = DynamoProxy.proxy(target, advice);
        // 代理类是 target.getClass() 的子类
        assertThat(proxy.getClass()).isNotSameAs(target.getClass());
        assertThat(target.getClass()).isAssignableFrom(proxy.getClass());
    }

    @Test
    void proxy_classWithoutNoArgCtor_throws() {
        assertThatThrownBy(() -> DynamoProxy.proxy(NoCtor.class, new RecordingAdvice()))
            .isInstanceOf(ProxyCreationException.class)
            .hasMessageContaining("no-arg constructor");
    }

    @Test
    void simpleAdvice_threadLocal_isolatedAcrossThreads() throws Exception {
        Calc target = new Calc();
        Calc proxy = DynamoProxy.proxy(target, new SimpleAdvice());
        AtomicInteger errors = new AtomicInteger();
        int threads = 4;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int v = i;
            ts[i] = new Thread(() -> {
                try {
                    int r = proxy.add(v, v);
                    assert r == v + v;
                } catch (Throwable t) {
                    errors.incrementAndGet();
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join(5000);
        assertThat(errors.get()).isZero();
    }
}
