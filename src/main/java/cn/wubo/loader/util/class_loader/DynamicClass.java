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
     * -cp <目录和 zip/jar 文件的类搜索路径>
     * -classpath <目录和 zip/jar 文件的类搜索路径>
     * --class-path <目录和 zip/jar 文件的类搜索路径>
     * 使用 ; 分隔的, 用于搜索类文件的目录, JAR 档案
     * 和 ZIP 档案列表。
     * -p <模块路径>
     * --module-path <模块路径>...
     * 用 ; 分隔的目录列表, 每个目录
     * 都是一个包含模块的目录。
     * --upgrade-module-path <模块路径>...
     * 用 ; 分隔的目录列表, 每个目录
     * 都是一个包含模块的目录, 这些模块
     * 用于替换运行时映像中的可升级模块
     * --add-modules <模块名称>[,<模块名称>...]
     * 除了初始模块之外要解析的根模块。
     * <模块名称> 还可以为 ALL-DEFAULT, ALL-SYSTEM,
     * ALL-MODULE-PATH.
     * --list-modules
     * 列出可观察模块并退出
     * -d <module name>
     * --describe-module <模块名称>
     * 描述模块并退出
     * --dry-run     创建 VM 并加载主类, 但不执行 main 方法。
     * 此 --dry-run 选项对于验证诸如
     * 模块系统配置这样的命令行选项可能非常有用。
     * --validate-modules
     * 验证所有模块并退出
     * --validate-modules 选项对于查找
     * 模块路径中模块的冲突及其他错误可能非常有用。
     * -D<名称>=<值>
     * 设置系统属性
     * -verbose:[class|module|gc|jni]
     * 为给定子系统启用详细输出
     * -version      将产品版本输出到错误流并退出
     * --version     将产品版本输出到输出流并退出
     * -showversion  将产品版本输出到错误流并继续
     * --show-version
     * 将产品版本输出到输出流并继续
     * --show-module-resolution
     * 在启动过程中显示模块解析输出
     * -? -h -help
     * 将此帮助消息输出到错误流
     * --help        将此帮助消息输出到输出流
     * -X            将额外选项的帮助输出到错误流
     * --help-extra  将额外选项的帮助输出到输出流
     * -ea[:<程序包名称>...|:<类名>]
     * -enableassertions[:<程序包名称>...|:<类名>]
     * 按指定的粒度启用断言
     * -da[:<程序包名称>...|:<类名>]
     * -disableassertions[:<程序包名称>...|:<类名>]
     * 按指定的粒度禁用断言
     * -esa | -enablesystemassertions
     * 启用系统断言
     * -dsa | -disablesystemassertions
     * 禁用系统断言
     * -agentlib:<库名>[=<选项>]
     * 加载本机代理库 <库名>, 例如 -agentlib:jdwp
     * 另请参阅 -agentlib:jdwp=help
     * -agentpath:<路径名>[=<选项>]
     * 按完整路径名加载本机代理库
     * -javaagent:<jar 路径>[=<选项>]
     * 加载 Java 编程语言代理, 请参阅 java.lang.instrument
     * -splash:<图像路径>
     * 使用指定的图像显示启动屏幕
     * 自动支持和使用 HiDPI 缩放图像
     * (如果可用)。应始终将未缩放的图像文件名 (例如, image.ext)
     * 作为参数传递给 -splash 选项。
     * 将自动选取提供的最合适的缩放
     * 图像。
     * 有关详细信息, 请参阅 SplashScreen API 文档
     *
     * @param options
     * @return
     * @argument 文件
     * 一个或多个包含选项的参数文件
     * -disable-@files
     * 阻止进一步扩展参数文件
     * --enable-preview
     * 允许类依赖于此发行版的预览功能
     * 要为长选项指定参数, 可以使用 --<名称>=<值> 或
     * --<名称> <值>。
     */
    public DynamicClass config(List<String> options) {
        log.debug("添加编译配置 options：{}", options.stream().collect(Collectors.joining(" ")));
        this.options = options;
        return this;
    }

    /**
     * 编译配置classpath
     *
     * @param classpath
     * @return
     */
    public DynamicClass addClasspath(String classpath) {
        log.debug("添加编译配置 classpath：{}", classpath);
        options.add("-classpath");
        options.add(classpath);
        return this;
    }

    /**
     * 编译Java代码
     *
     * @return 返回DynamicClass对象
     */
    public DynamicClass compiler() {
        log.debug("开始执行编译");

        // 获取Java编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // 创建诊断收集器
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();

        // 创建内存文件管理器
        this.fileManager = new MemFileManager(compiler.getStandardFileManager(diagnosticCollector, null, null));

        // 创建JavaMemSource对象
        JavaMemSource file = new JavaMemSource(fullClassName, javaSourceCode);

        // 创建编译单元Iterable
        Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(file);

        log.debug("获取编译任务");
        // 获取编译任务
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, options, null, compilationUnits);

        log.debug("执行编译");
        boolean result = task.call();
        // 执行编译任务
        if (!result) {
            // 处理编译错误
            String errorMessage = diagnosticCollector.getDiagnostics().stream()
                    .map(Object::toString)
                    .reduce("", (acc, x) -> acc + "\r\n" + x);
            log.debug("编译失败: {}", errorMessage);
            throw new LoaderRuntimeException("编译失败: " + errorMessage);
        }
        log.debug("编译成功");
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
