package cn.wubo.dynamo.spring.compiler;

import cn.wubo.dynamo.spring.exception.CompilationException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态编译 + 类加载的容器。
 *
 * <p>每个实例持有独立的字节码缓存与 classpath 状态；不依赖任何全局单例。
 * 关闭后实例不可再用。
 */
public class DynamoClassLoader extends URLClassLoader implements AutoCloseable {

    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    /** 默认父 ClassLoader 工厂方法。父为当前线程的 context ClassLoader。 */
    public static DynamoClassLoader create() {
        return new DynamoClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public static DynamoClassLoader create(ClassLoader parent) {
        return new DynamoClassLoader(parent);
    }

    protected DynamoClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        ensureSystemCompilerAvailable();
    }

    private static void ensureSystemCompilerAvailable() {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            throw new IllegalStateException(
                "Dynamic compilation requires a JDK; current JRE has no system compiler. " +
                "Run with a JDK, e.g. `java` from $JAVA_HOME.");
        }
    }

    // ---------- 编译 ----------

    /**
     * 编译源码。失败返回 {@link CompilationResult}（success=false），不抛异常。
     */
    public CompilationResult compile(String sourceCode) {
        return compile(sourceCode, CompilerOptions.create());
    }

    public CompilationResult compile(String sourceCode, CompilerOptions opts) {
        checkNotClosed();
        String className;
        try {
            className = parseClassName(sourceCode);
        } catch (RuntimeException parseEx) {
            // Source is so broken that JavaParser can't extract a class name.
            // Fall back to a synthetic name so the real compiler can produce the diagnostics.
            className = "Unknown";
        }
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            List<JavaFileObject> compilationUnits = new ArrayList<>();
            compilationUnits.add(new MemoryJavaFileObject(className, sourceCode));

            try (StandardJavaFileManager standardFm =
                     compiler.getStandardFileManager(diagnostics, null, null)) {
                MemoryFileManager mgr = new MemoryFileManager(standardFm, this);
                JavaCompiler.CompilationTask task = compiler.getTask(
                    null, mgr, diagnostics, opts.build(), null, compilationUnits);
                boolean success = task.call();
                if (success) {
                    Class<?> clazz = findClass(className);
                    return new CompilationResult(true, className, clazz, List.of(), "");
                } else {
                    String msg = diagnostics.getDiagnostics().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));
                    return new CompilationResult(false, className, null,
                        new ArrayList<>(diagnostics.getDiagnostics()), msg);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new CompilationException("Failed to compile: " + e.getMessage(), e);
        }
    }

    /** 编译并加载；失败抛 {@link CompilationException}。 */
    public Class<?> compileAndLoad(String sourceCode) {
        return compileAndLoad(sourceCode, CompilerOptions.create());
    }

    public Class<?> compileAndLoad(String sourceCode, CompilerOptions opts) {
        CompilationResult result = compile(sourceCode, opts);
        if (!result.isSuccess()) {
            throw new CompilationException(result.getErrorMessage(), result);
        }
        return result.getCompiledClass();
    }

    // ---------- 类定义（外部字节码）----------

    public Class<?> defineClass(String name, byte[] bytes) {
        checkNotClosed();
        classes.put(name, bytes);
        try {
            return findClass(name);
        } catch (ClassNotFoundException e) {
            throw new CompilationException("Failed to define class: " + name, e);
        }
    }

    // ---------- 类路径 ----------

    public synchronized void addJarPath(String jarPath) {
        checkNotClosed();
        File jarFile = new File(jarPath);
        if (!jarFile.isFile()) {
            throw new CompilationException("JAR not found: " + jarPath);
        }
        try {
            addURL(new URL("jar:file:" + jarFile.getAbsolutePath() + "!/"));
        } catch (MalformedURLException e) {
            throw new CompilationException("Invalid jar path: " + jarPath, e);
        }
    }

    public void addJarPaths(String... jarPaths) {
        for (String p : jarPaths) {
            addJarPath(p);
        }
    }

    /**
     * Adds a resource directory (not a JAR) to the classpath.
     * @param path filesystem path to a directory containing .class files or resources
     */
    public synchronized void addResourcePath(String path) {
        checkNotClosed();
        try {
            addURL(new File(path).toURI().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid resource path: " + path, e);
        }
    }

    // ---------- 生命周期 ----------

    @Override
    public void close() {
        closed = true;
        classes.clear();
        try {
            super.close();
        } catch (IOException ignored) {
            // URLClassLoader.close 在 JDK 17 是 no-op
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("DynamoClassLoader has been closed");
        }
    }

    // ---------- 内部 SPI ----------

    /** 由 {@link MemoryClassFileObject} 调用，把编译产物存到本实例缓存。 */
    void registerCompiledClass(String name, byte[] bytes) {
        classes.put(name, bytes);
    }

    // ---------- ClassLoader 重写 ----------

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> existing = findLoadedClass(name);
        if (existing != null) return existing;

        byte[] bytes = classes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }

    // ---------- 工具 ----------

    /** 解析源码中的类名（含包名）。无包名时只返回类名。 */
    public static String parseClassName(String sourceCode) {
        CompilationUnit unit = StaticJavaParser.parse(sourceCode);
        String packageName = unit.getPackageDeclaration()
            .map(PackageDeclaration::getNameAsString)
            .orElse("");
        String className = unit.getTypes().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No className found in sourceCode"))
            .getNameAsString();
        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}
