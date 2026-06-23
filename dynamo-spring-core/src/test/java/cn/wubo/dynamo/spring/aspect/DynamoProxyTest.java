package cn.wubo.dynamo.spring.aspect;

import cn.wubo.dynamo.spring.exception.ProxyCreationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoProxyTest {

    public static class Service {
        public String greet(String name) { return "hi " + name; }
        public int doubleIt(int n) { return n * 2; }
        public void throwIt() { throw new IllegalStateException("boom"); }
    }

    public static class NoCtorService {
        public NoCtorService(String x) {}
    }

    static class CountingAdvice implements IAdvice {
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        AtomicInteger afterThrow = new AtomicInteger();

        @Override public void before(Object t, Method m, Object[] a) { before.incrementAndGet(); }
        @Override public void after(Object t, Method m, Object[] a, Object r) { after.incrementAndGet(); }
        @Override public void afterThrow(Object t, Method m, Object[] a, Throwable c) { afterThrow.incrementAndGet(); }
    }

    @Test
    void proxy_routesToTarget() {
        Service p = DynamoProxy.proxy(Service.class, new CountingAdvice());
        assertThat(p.greet("alice")).isEqualTo("hi alice");
        assertThat(p.doubleIt(21)).isEqualTo(42);
    }

    @Test
    void proxy_invokesBeforeAndAfter() {
        CountingAdvice advice = new CountingAdvice();
        Service p = DynamoProxy.proxy(Service.class, advice);
        p.greet("x");
        assertThat(advice.before.get()).isEqualTo(1);
        assertThat(advice.after.get()).isEqualTo(1);
        assertThat(advice.afterThrow.get()).isZero();
    }

    @Test
    void proxy_invokesAfterThrow_andRethrows() {
        CountingAdvice advice = new CountingAdvice();
        Service p = DynamoProxy.proxy(Service.class, advice);
        assertThatThrownBy(p::throwIt)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
        assertThat(advice.afterThrow.get()).isEqualTo(1);
    }

    @Test
    void proxy_targetOverload_extractsClass() {
        Service target = new Service();
        CountingAdvice advice = new CountingAdvice();
        Service p = DynamoProxy.proxy(target, advice);
        assertThat(p.greet("bob")).isEqualTo("hi bob");
    }

    @Test
    void proxy_noNoArgConstructor_throwsProxyCreationException() {
        assertThatThrownBy(() -> DynamoProxy.proxy(NoCtorService.class, new CountingAdvice()))
            .isInstanceOf(ProxyCreationException.class)
            .hasMessageContaining("no-arg constructor");
    }
}
