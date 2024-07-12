package cn.wubo.loader.util.class_loader;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.tools.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将java内容编译成class，放入内存中
 */
@Slf4j
public class DynamicClass {
    private String javaSourceCode;
    @Getter
    private String fullClassName;
    private List<String> options = new ArrayList<>();
    private MemFileManager fileManager;

    public DynamicClass(String javaSourceCode, String fullClassName) {
        this.javaSourceCode = javaSourceCode;
        this.fullClassName = fullClassName;
    }

    /**
     * 初始化一个DynamicClass对象
     *
     * @param javaSourceCode Java源代码字符串
     * @param fullClassName  完整的类名
     * @return 初始化后的DynamicClass对象
     */
    public static DynamicClass init(String javaSourceCode, String fullClassName) {
        log.debug("初始化class javaSourceCode:{} fullClassName:{}", javaSourceCode, fullClassName);
        return new DynamicClass(javaSourceCode, fullClassName);
    }

    /**
     * 配置编译选项。
     *
     * 通过此方法可以为动态类的编译过程设置一系列选项，这些选项将以列表的形式提供，并可能影响编译器的行为或生成类的特性。
     *
     * 1. -cp 或 -classpath: 指定查找用户类文件和包的位置。
     * 2. -d: 指定存放生成的类文件的位置。
     * 3. -sourcepath: 指定查找输入源文件的位置。
     * 4. -encoding: 指定源文件的字符编码。
     * 5. -g: 指定是否生成所有调试信息，开关参数。
     * 6. -g:none: 不生成任何调试信息。
     * 7. -g:{lines,vars,source}：只生成特定类型的调试信息。
     * 8. -nowarn: 不生成任何警告信息。
     * 9. -verbose: 输出有关编译器正在执行的操作的消息。
     * 10. -deprecation: 输出使用了不推荐使用的类或方法的源位置的警告信息。
     * 11. -Werror: 将警告当做错误处理。
     *
     * @param options 编译选项的列表，每个选项作为一个字符串。
     * @return 返回DynamicClass的实例，允许链式调用。
     */
    public DynamicClass config(List<String> options) {
        // 使用日志记录当前配置的编译选项，以便于调试和跟踪
        log.debug("添加编译配置 options：{}", String.join(" ", options));
        this.options = options;
        return this;
    }

    /**
     * 向编译器选项中添加类路径。
     *
     * 通过此方法，可以在编译过程中指定额外的类路径，以便编译器能够找到所需的类和资源。
     * 这对于处理依赖于外部库或资源的项目尤其有用。
     *
     * @param classpath 要添加的类路径，可以是文件系统中的路径或JAR文件的路径。
     * @return 返回DynamicClass的实例，允许链式调用。
     */
    public DynamicClass addClasspath(String classpath) {
        // 日志记录添加的类路径，以便于调试和跟踪。
        log.debug("添加编译配置 classpath：{}", classpath);
        // 将-classpath选项和指定的类路径添加到编译器选项列表中。
        options.add("-classpath");
        options.add(classpath);
        // 返回当前实例，支持链式调用。
        return this;
    }

    /**
     * 动态编译Java源代码。
     * 使用Java编译器API编译存储在内存中的Java源代码。
     * 如果编译失败，将抛出LoaderRuntimeException异常。
     *
     * @return DynamicClass 实例，支持方法链式调用。
     * @throws LoaderRuntimeException 如果编译失败，抛出此异常。
     */
    public DynamicClass compiler() {
        // 开始编译过程的日志记录
        log.debug("开始执行编译");
        // 获取系统Java编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // 创建一个用于收集编译器诊断信息的收集器
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        // 初始化文件管理器，用于在内存中管理源文件
        this.fileManager = new MemFileManager(compiler.getStandardFileManager(diagnosticCollector, null, null));
        // 创建一个内存中的Java源文件对象
        JavaMemSource file = new JavaMemSource(fullClassName, javaSourceCode);
        // 将内存中的源文件作为编译单元
        Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(file);
        // 获取编译任务
        log.debug("获取编译任务");
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, options, null, compilationUnits);
        // 执行编译任务
        log.debug("执行编译");
        // 编译结果
        boolean result = task.call();
        // 如果编译失败
        if (!result) {
            // 组合编译错误信息
            String errorMessage = diagnosticCollector.getDiagnostics().stream()
                    .map(Object::toString)
                    .reduce("", (acc, x) -> acc + "\r\n" + x);
            // 记录编译失败的日志
            log.debug("编译失败: {}", errorMessage);
            // 抛出编译失败的异常
            throw new LoaderRuntimeException("编译失败: " + errorMessage);
        }
        // 编译成功的日志记录
        log.debug("编译成功");
        // 返回当前实例，支持方法链式调用
        return this;
    }

    /**
     * 加载指定类的类对象。
     *
     * @return 返回指定类的类对象。
     * @throws LoaderRuntimeException 如果找不到指定类则抛出该异常。
     */
    public Class<?> load() {
        try {
            // 获取已编译的类数据
            Map<String, byte[]> compiledClasses = fileManager.getAllCompiledClassesData();
            // 创建动态类加载器
            DynamicClassLoader classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
            // 将已编译的类数据添加到动态类加载器中
            compiledClasses.forEach(classLoader::addClass);
            // 加载指定类的类对象
            return classLoader.loadClass(fullClassName);
        } catch (ClassNotFoundException e) {
            // 加载类失败，抛出LoaderRuntimeException异常
            throw new LoaderRuntimeException("加载类失败: " + e.getMessage(), e);
        }
    }

}
