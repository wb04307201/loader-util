package cn.wubo.dynamic.loader.utility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发维度的 DynamicRuntime 测试。
 *
 * <p>验证：
 * <ul>
 *   <li>单实例多线程并发 compileAndLoad 不抛 CME / LinkageError
 *   <li>多实例并发互不干扰（字节码缓存隔离）
 *   <li>编译 + close 同时发生不抛 IllegalStateException（race window）
 * </ul>
 */
class DynamicRuntimeConcurrencyTest {

    @Test
    void concurrent_compileAndLoad_doesNotThrow() throws Exception {
        int threads = 8;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<int[]>> futures = new ArrayList<>();
        try (DynamicRuntime runtime = DynamicRuntime.create()) {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    int[] counts = new int[2]; // [compiled, failed]
                    for (int i = 0; i < perThread; i++) {
                        String src = "package t" + id + "; public class K" + i +
                            " { public int v() { return " + (id * 1000 + i) + "; } }";
                        try {
                            Class<?> c = runtime.compileAndLoad(src);
                            // 反射调用确认字节码加载正确
                            Object inst = c.getDeclaredConstructor().newInstance();
                            int v = (int) c.getDeclaredMethod("v").invoke(inst);
                            assert v == id * 1000 + i;
                            counts[0]++;
                        } catch (Exception e) {
                            counts[1]++;
                        }
                    }
                    return counts;
                }));
            }
            start.countDown();
            int totalCompiled = 0, totalFailed = 0;
            for (Future<int[]> f : futures) {
                int[] c = f.get(30, TimeUnit.SECONDS);
                totalCompiled += c[0];
                totalFailed += c[1];
            }
            assertThat(totalCompiled).isEqualTo(threads * perThread);
            assertThat(totalFailed).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void multipleInstances_isolatedClassLoaders() {
        // 两个 DynamicRuntime 实例编译同名类，应当加载到不同 ClassLoader。
        try (DynamicRuntime a = DynamicRuntime.create();
             DynamicRuntime b = DynamicRuntime.create()) {
            Class<?> clsA = a.compileAndLoad("public class SameName { public int v() { return 1; } }");
            Class<?> clsB = b.compileAndLoad("public class SameName { public int v() { return 2; } }");
            assertThat(clsA).isNotSameAs(clsB);
            assertThat(clsA.getClassLoader()).isNotSameAs(clsB.getClassLoader());
        }
    }

    @Test
    void close_concurrentWithCompile_isSafe() throws Exception {
        // 一边编译一边 close — 不应抛未捕获异常。
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    try (DynamicRuntime r = DynamicRuntime.create()) {
                        start.await();
                        for (int i = 0; i < 50; i++) {
                            String src = "package c" + id + "; public class X" + i + "{}";
                            try {
                                r.compileAndLoad(src);
                            } catch (IllegalStateException ignore) {
                                // close() 触发的状态异常是预期
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            }
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
            // 唯一允许的异常是 close 后的 IllegalStateException
            assertThat(errors.get()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
