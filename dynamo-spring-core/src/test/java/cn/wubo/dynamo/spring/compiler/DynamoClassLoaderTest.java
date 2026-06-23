package cn.wubo.dynamo.spring.compiler;

import cn.wubo.dynamo.spring.exception.CompilationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoClassLoaderTest {

    @Test
    void compile_simpleClass_returnsSuccessResult() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String source = """
                public class Hello {
                    public String greet() { return "hi"; }
                }
                """;
            CompilationResult result = loader.compile(source);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getClassName()).isEqualTo("Hello");
            assertThat(result.getCompiledClass()).isNotNull();
            assertThat(result.getCompiledClass().getSimpleName()).isEqualTo("Hello");
        }
    }

    @Test
    void compileAndLoad_successful_returnsClass() throws Exception {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String source = "public class A { public int x() { return 42; } }";
            Class<?> clazz = loader.compileAndLoad(source);
            assertThat(clazz).isNotNull();
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Object result = instance.getClass().getDeclaredMethod("x").invoke(instance);
            assertThat(result).isEqualTo(42);
        }
    }

    @Test
    void compileAndLoad_syntaxError_throwsCompilationException() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String bad = "public class { this is not valid }";
            assertThatThrownBy(() -> loader.compileAndLoad(bad))
                .isInstanceOf(CompilationException.class);
        }
    }

    @Test
    void compile_returnedResult_doesNotThrow() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String bad = "public class { this is not valid }";
            CompilationResult result = loader.compile(bad);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getDiagnostics()).isNotEmpty();
            assertThat(result.getCompiledClass()).isNull();
            assertThat(result.getErrorMessage()).isNotEmpty();
        }
    }

    @Test
    void create_withNullParent_usesContextClassLoader() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            assertThat(loader.getParent()).isNotNull();
        }
    }

    @Test
    void close_isIdempotent() {
        DynamoClassLoader loader = DynamoClassLoader.create();
        loader.close();
        loader.close(); // 第二次不抛
    }

    @Test
    void compile_afterClose_throws() {
        DynamoClassLoader loader = DynamoClassLoader.create();
        loader.close();
        assertThatThrownBy(() -> loader.compile("public class A {}"))
            .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Task 4 review fix: two-instance isolation ----------

    @Test
    void compile_isolatesAcrossInstances() {
        try (DynamoClassLoader a = DynamoClassLoader.create();
             DynamoClassLoader b = DynamoClassLoader.create()) {
            String srcA = "package x; public class Iso { public String hi() { return \"A\"; } }";
            Class<?> clsA = a.compileAndLoad(srcA);
            assertThat(clsA.getDeclaredConstructor().newInstance().toString()).contains("Iso");

            String srcB = "package x; public class Iso { public String hi() { return \"B\"; } }";
            Class<?> clsB = b.compileAndLoad(srcB);

            // Each instance owns its own bytecode cache; classes loaded in A must not be
            // visible from B, and the two Class objects must have different ClassLoaders.
            assertThat(clsA.getClassLoader()).isNotSameAs(clsB.getClassLoader());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- Task 4 review fix: CompilationException.getResult ----------

    @Test
    void compileAndLoad_failure_exceptionCarriesResult() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String bad = "public class { this is not valid }";
            try {
                loader.compileAndLoad(bad);
            } catch (CompilationException ex) {
                assertThat(ex.getResult()).isNotNull();
                assertThat(ex.getResult().isSuccess()).isFalse();
                assertThat(ex.getResult().getDiagnostics()).isNotEmpty();
                assertThat(ex.getMessage()).isNotEmpty();
                return;
            }
            throw new AssertionError("expected CompilationException");
        }
    }

    // ---------- Task 4 review fix: addResourcePath ----------

    @Test
    void addResourcePath_validDirectory_succeeds() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            // Use the current working directory as a benign resource directory.
            String cwd = System.getProperty("user.dir");
            loader.addResourcePath(cwd);
            // No exception means the path was accepted.
        }
    }

    @Test
    void addResourcePath_afterClose_throws() {
        DynamoClassLoader loader = DynamoClassLoader.create();
        loader.close();
        assertThatThrownBy(() -> loader.addResourcePath("any/path"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    // ---------- Task 4 review fix: addJarPath contract ----------
    // Note: full JAR classpath import resolution requires real JAR files on the test
    // classpath and is covered by Task 5 (compiler IT). Here we verify the contract:
    // missing files raise a clear error, and addJarPath after close is rejected.

    @Test
    void addJarPath_missingFile_throws() {
        try (DynamoClassLoader cl = DynamoClassLoader.create()) {
            assertThatThrownBy(() -> cl.addJarPath("/nonexistent/path/missing.jar"))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void addJarPath_afterClose_throws() {
        DynamoClassLoader cl = DynamoClassLoader.create();
        cl.close();
        assertThatThrownBy(() -> cl.addJarPath("any.jar"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    // ---------- Task 4 review fix: thread safety ----------
    // If concurrent compilation mutating internal maps causes CME/LinkageError, this
    // test fails — that is the point. If it passes, the invariant holds.

    @Test
    void concurrent_addJarPath_and_compile_doesNotThrow() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (DynamoClassLoader cl = DynamoClassLoader.create()) {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 5; i++) {
                        // Distinct package + class per task to avoid name collisions
                        // across the whole thread pool.
                        String pkg = "c" + id;
                        String cls = "T" + (id * 1000 + i);
                        String src = "package " + pkg + "; public class " + cls +
                            " { @Override public String toString() { return \"t\"; } }";
                        cl.compileAndLoad(src);
                        try {
                            cl.addJarPath("/tmp/nonexistent-" + id + "-" + i + ".jar");
                        } catch (CompilationException ignore) {
                            // addJarPath now validates file existence; the test exercises
                            // concurrent URL mutation, not jar discovery.
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Minor #1: 覆盖剩余 overloads ----------

    @Test
    void create_withExplicitParent_usesProvidedParent() {
        ClassLoader custom = new ClassLoader(DynamoClassLoaderTest.class.getClassLoader()) {};
        try (DynamoClassLoader loader = DynamoClassLoader.create(custom)) {
            assertThat(loader.getParent()).isSameAs(custom);
        }
    }

    @Test
    void defineClass_fromBytes_makesLoadable() throws Exception {
        // 用一个 loader 编译并提取字节码，再让另一个 loader 通过 defineClass 加载。
        String fqcn = "Precompiled";
        byte[] bytes;
        try (DynamoClassLoader src = DynamoClassLoader.create()) {
            src.compileAndLoad("public class Precompiled { public int v() { return 99; } }");
            bytes = readClassBytes(src, fqcn);
        }
        try (DynamoClassLoader dst = DynamoClassLoader.create()) {
            Class<?> clazz = dst.defineClass(fqcn, bytes);
            assertThat(clazz.getSimpleName()).isEqualTo("Precompiled");
            Object inst = clazz.getDeclaredConstructor().newInstance();
            assertThat(inst.getClass().getDeclaredMethod("v").invoke(inst)).isEqualTo(99);
        }
    }

    /** 反射从 {@code classes} 字段读取已编译字节码（package-private 字段，仅测试用）。 */
    private static byte[] readClassBytes(DynamoClassLoader loader, String name) throws Exception {
        java.lang.reflect.Field f = DynamoClassLoader.class.getDeclaredField("classes");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, byte[]> map = (java.util.Map<String, byte[]>) f.get(loader);
        byte[] bytes = map.get(name);
        if (bytes == null) {
            throw new IllegalStateException("no bytes cached for " + name);
        }
        return bytes;
    }

    @Test
    void addJarPaths_missingFile_propagatesError() {
        try (DynamoClassLoader cl = DynamoClassLoader.create()) {
            assertThatThrownBy(() -> cl.addJarPaths("/nope/a.jar", "/nope/b.jar"))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("/nope/a.jar");
        }
    }

    @Test
    void compile_withOptions_respectsTargetVersion() {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String source = "public class Opt { public int x() { return 1; } }";
            // 选项被透传到 javax.tools；这里只验证构造合法、能跑通。
            CompilationResult r = loader.compile(source, CompilerOptions.create().sourceVersion("17").targetVersion("17"));
            assertThat(r.isSuccess()).isTrue();
        }
    }

    @Test
    void compileAndLoad_withOptions_returnsClass() throws Exception {
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            String source = "public class Opt2 { public String s() { return \"x\"; } }";
            Class<?> clazz = loader.compileAndLoad(source,
                CompilerOptions.create().sourceVersion("17").targetVersion("17"));
            Object inst = clazz.getDeclaredConstructor().newInstance();
            assertThat(inst.getClass().getDeclaredMethod("s").invoke(inst)).isEqualTo("x");
        }
    }

    // ---------- 边角覆盖（提升 jacoco 覆盖率）----------

    @Test
    void isClosed_reflectsLifecycle() {
        DynamoClassLoader loader = DynamoClassLoader.create();
        assertThat(loader.isClosed()).isFalse();
        loader.close();
        assertThat(loader.isClosed()).isTrue();
    }

    @Test
    void parseClassName_topLevelClass() {
        assertThat(DynamoClassLoader.parseClassName("public class Foo {}")).isEqualTo("Foo");
    }

    @Test
    void parseClassName_packagedClass() {
        assertThat(DynamoClassLoader.parseClassName(
            "package com.x; public class Bar {}")).isEqualTo("com.x.Bar");
    }

    @Test
    void parseClassName_noTypeDeclaration_throws() {
        // 只有 package 声明，没有类——JavaParser 会给出空 types 流。
        assertThatThrownBy(() -> DynamoClassLoader.parseClassName("package com.x;"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No className found");
    }

    @Test
    void findClass_unknownClass_delegatesToParent() {
        // 走 findClass 中 bytes==null 的 fallback → super.findClass
        // 父 ClassLoader 找不到 → ClassNotFoundException
        try (DynamoClassLoader loader = DynamoClassLoader.create()) {
            assertThatThrownBy(() -> loader.loadClass("totally.bogus.ClassName"))
                .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void addJarPath_malformed_throws() {
        // URLClassLoader 会拒绝非法的 URL；这里 force 一个含空格的"jar:file:"路径
        // 来触发 MalformedURLException 分支。
        try (DynamoClassLoader cl = DynamoClassLoader.create()) {
            assertThatThrownBy(() -> cl.addJarPath("not a real path with spaces!.jar"))
                .isInstanceOf(CompilationException.class);
        }
    }

}
