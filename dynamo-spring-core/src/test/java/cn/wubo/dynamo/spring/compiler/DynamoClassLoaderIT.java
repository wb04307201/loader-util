package cn.wubo.dynamo.spring.compiler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoClassLoaderIT {

    @Test
    void twoLoaders_canHoldSameFQCN_simultaneously() throws Exception {
        String srcV1 = "public class SameName { public String version() { return \"v1\"; } }";
        String srcV2 = "public class SameName { public String version() { return \"v2\"; } }";

        try (DynamoClassLoader l1 = DynamoClassLoader.create();
             DynamoClassLoader l2 = DynamoClassLoader.create()) {

            Class<?> c1 = l1.compileAndLoad(srcV1);
            Class<?> c2 = l2.compileAndLoad(srcV2);

            assertThat(c1).isNotSameAs(c2);
            assertThat(c1.getClassLoader()).isSameAs(l1);
            assertThat(c2.getClassLoader()).isSameAs(l2);

            Method m1 = c1.getDeclaredMethod("version");
            Method m2 = c2.getDeclaredMethod("version");
            assertThat(m1.invoke(c1.getDeclaredConstructor().newInstance())).isEqualTo("v1");
            assertThat(m2.invoke(c2.getDeclaredConstructor().newInstance())).isEqualTo("v2");
        }
    }

    @Test
    void jarPath_allowsImportFromJar() throws Exception {
        // spring-core 在 test classpath 上
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            loader.addJarPath(findSpringCoreJar());
            String src = """
                import org.springframework.util.StopWatch;
                public class UsesStopWatch {
                    public String name() {
                        StopWatch sw = new StopWatch("test");
                        return sw.getId();
                    }
                }
                """;
            Class<?> clazz = loader.compileAndLoad(src);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            assertThat(clazz.getMethod("name").invoke(instance)).isEqualTo("test");
        }
    }

    private String findSpringCoreJar() {
        // 由 Maven test classpath 提供；通过 classloader 找到
        java.net.URL u = org.springframework.util.StopWatch.class.getProtectionDomain()
            .getCodeSource().getLocation();
        return new java.io.File(u.getPath()).getAbsolutePath();
    }

    @Test
    void concurrent_addJarPathAndCompile_doesNotThrow() throws Exception {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            int threads = 10;
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                int idx = i;
                new Thread(() -> {
                    try {
                        start.await();
                        String src = "public class T" + idx + " { public int x() { return " + idx + "; } }";
                        Class<?> c = loader.compileAndLoad(src);
                        assertThat(c).isNotNull();
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }
            start.countDown();
            assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
}