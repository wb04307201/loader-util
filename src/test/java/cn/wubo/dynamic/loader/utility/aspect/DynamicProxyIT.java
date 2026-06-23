package cn.wubo.dynamic.loader.utility.aspect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicProxyIT {

    public static class Counter {
        private final AtomicInteger n = new AtomicInteger();
        public int increment() { return n.incrementAndGet(); }
        public void throwOp() { throw new RuntimeException("x"); }
    }

    static class CountingAdvice implements IAdvice {
        final AtomicInteger before = new AtomicInteger();
        final AtomicInteger after = new AtomicInteger();
        final AtomicInteger afterThrow = new AtomicInteger();
        public void before(Object t, Method m, Object[] a) { before.incrementAndGet(); }
        public void after(Object t, Method m, Object[] a, Object r) { after.incrementAndGet(); }
        public void afterThrow(Object t, Method m, Object[] a, Throwable c) { afterThrow.incrementAndGet(); }
    }

    @Test
    void threadSafety_100threads_invocationsBalanced() throws Exception {
        CountingAdvice advice = new CountingAdvice();
        Counter counter = DynamicProxy.proxy(Counter.class, advice);

        int threads = 100;
        int iters = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iters; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

        int total = threads * iters;
        assertThat(advice.before.get()).isEqualTo(total);
        assertThat(advice.after.get()).isEqualTo(total);
        assertThat(advice.afterThrow.get()).isZero();
        assertThat(counter.n.get()).isEqualTo(total);
    }

    @Test
    void simpleAdvice_twoProxies_shareInstance_doNotInterfere() throws Exception {
        SimpleAdvice advice = new SimpleAdvice();
        Counter c1 = DynamicProxy.proxy(Counter.class, advice);
        Counter c2 = DynamicProxy.proxy(Counter.class, advice);

        // 串行调用（不同线程的并发由上面覆盖）
        c1.increment();
        c1.increment();
        c2.increment();
        // 不抛异常即通过；SimpleAdvice 内部 ThreadLocal 隔离
        assertThat(c1.n.get()).isEqualTo(2);
        assertThat(c2.n.get()).isEqualTo(1);
    }

    @Test
    void methodDelegation_superCallActuallyRuns() {
        CountingAdvice advice = new CountingAdvice();
        Counter c = DynamicProxy.proxy(Counter.class, advice);
        assertThat(c.increment()).isEqualTo(1);
        assertThat(c.increment()).isEqualTo(2);
    }
}
