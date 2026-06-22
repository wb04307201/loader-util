# dynamic-loader-utility 2.0 重构设计

- **日期**：2026-06-23
- **目标版本**：2.0.0-SNAPSHOT
- **作者**：brainstorming 流程产出
- **基线**：Spring Boot 3.5.x，JDK 17
- **范围**：从 1.x 全量重构到 2.0

## 一、目标与范围

### 目标
把 `dynamic-loader-utility` 从"无状态工具集 + 共享单例 ClassLoader"重塑为"显式生命周期的运行时容器"：
- 三个能力（编译、AOP、Bean）均有显式实例与 `close()` 生命周期
- 不再依赖反射 Spring 私有 API
- 用 ByteBuddy 替代 CGLIB
- ClassLoader 隔离、版本管理、并发安全

### 范围（all-in）
- 三个包全部重构
- 基础设施补齐：测试、CI、版本号、README
- 旧静态 API 全部删除（2.0 breaking change）
- 单一仓库（不分 module）

### 非目标
- 不引入多模块拆分（先看一个 jar 的复杂度）
- 不做 bytecode instrumentation 层（与 Spring AOP 重叠）
- 不做 Java agent / premain 支持

## 二、整体结构

```
cn.wubo.dynamic.loader.utility
├── compiler
│   ├── DynamicClassLoader          ← 核心：可独立实例化的 ClassLoader + 编译 + 类定义
│   ├── CompilerOptions             ← 增强：增加 sourcepath / classpath 选项
│   ├── CompilationResult           ← 新：结构化诊断信息
│   ├── MemoryFileManager           ← 重构：绑定到具体 DynamicClassLoader 实例
│   ├── MemoryJavaFileObject
│   └── MemoryClassFileObject
├── aspect
│   ├── DynamicProxy                ← 替换 DynamicAspect，ByteBuddy 实现
│   ├── IAdvice                     ← 替换 IAspect（同三方法签名）
│   ├── SimpleAdvice                ← 替换 SimpleAspect，ThreadLocal 计时
│   └── AdviceInterceptor           ← ByteBuddy @RuntimeType 适配
├── bean
│   ├── DynamicRequestMappingHandlerMapping  ← 新：RequestMappingHandlerMapping 子类
│   ├── DynamicBean                 ← 瘦身后的门面
│   └── DynamicBeanAutoConfiguration ← 新：Spring Boot 3.x 自动配置
├── exception
│   ├── BeanRegistrationException
│   ├── CompilationException
│   └── ProxyCreationException
└── DynamicRuntime                  ← 新：高级门面
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：
```
cn.wubo.dynamic.loader.utility.bean.DynamicBeanAutoConfiguration
```

## 三、Compiler 包设计

### 3.1 DynamicClassLoader

合并现有 `DynamicCompiler` 静态工具类 + `ByteArrayClassLoader` 单例为单一有状态对象。

```java
public class DynamicClassLoader extends URLClassLoader implements AutoCloseable {

    // 工厂
    public static DynamicClassLoader create();
    public static DynamicClassLoader create(ClassLoader parent);

    // 编译
    public CompilationResult compile(String sourceCode);
    public CompilationResult compile(String sourceCode, CompilerOptions opts);
    public Class<?> compileAndLoad(String sourceCode);
    public Class<?> compileAndLoad(String sourceCode, CompilerOptions opts);

    // 类定义（外部字节码）
    public Class<?> defineClass(String name, byte[] bytes);

    // 类路径
    public void addJarPath(String jarPath);
    public void addJarPaths(String... jarPaths);
    public void addResourcePath(String path);

    // 生命周期
    @Override
    public void close();

    // 内部 SPI（package-private）
    void registerCompiledClass(String name, byte[] bytes);
}
```

#### 不变量
- 每个 `DynamicClassLoader` 实例**独立**持有 `Map<String, byte[]>`、`MemoryFileManager`、`StandardJavaFileManager`
- `close()` 之后该实例不能再 `compile` / `defineClass`，否则抛 `IllegalStateException`
- `close()` 幂等
- `addJarPath` 用 `synchronized(this)` 保护（`URLClassPath` 非线程安全）
- `findClass` 优先查本实例字节码 Map → fallback 父 ClassLoader，保留 `findLoadedClass` 短路

### 3.2 配套类

#### CompilationResult
```java
public final class CompilationResult {
    public boolean isSuccess();
    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics();
    public Class<?> getCompiledClass();  // 成功才有值
    public String getClassName();
    public String getErrorMessage();      // 已格式化多行错误
}
```

#### CompilationException
定义在 `exception` 包，compiler 包 import 使用：

```java
// 位于 cn.wubo.dynamic.loader.utility.exception
public class CompilationException extends RuntimeException {
    private final CompilationResult result;
    public CompilationResult getResult();
}
```

#### CompilerOptions
保留现有链式 API，新增：
- `sourcepath(String... paths)`
- `classpath(String... paths)`
- `enablePreview()`
- `addExtraClassLoader(ClassLoader)` —— 把当前 loader 的 classpath 透传给编译器解析 import

#### MemoryFileManager / MemoryJavaFileObject / MemoryClassFileObject
- 三个文件对象类全部重写
- 构造时必须传入所属的 `DynamicClassLoader` 实例（不再用 `getInstance()` 单例）
- 编译任务执行时，本实例的 `MemoryFileManager` 写出的字节码 → 回调 `loader.registerCompiledClass(name, bytes)`，落到**本实例的 Map** 里
- 跨实例隔离达成

### 3.3 移除

| 移除 | 原因 |
| --- | --- |
| `DynamicCompiler` 静态类 | 全部逻辑下沉到 `DynamicClassLoader` |
| `ByteArrayClassLoader` 公开类 | 被 `DynamicClassLoader` 取代 |
| `ByteArrayClassLoader.getInstance()` 单例 | 整个项目不再有单例 |
| `clear()` 方法 | 改成实例级 `close()` |

### 3.4 错误处理
- **编译失败**：返回 `CompilationResult(success=false, diagnostics=...)`，**不抛异常**（2.0 新契约，调用方用 `compile` 判断；想快速失败用 `compileAndLoad` 抛 `CompilationException`）
- **`ToolProvider.getSystemJavaCompiler()` 返回 null**（非 JDK 环境）：构造阶段抛清晰错误 `IllegalStateException("Dynamic compilation requires a JDK; current JRE has no system compiler. Run with a JDK, e.g. `java` from $JAVA_HOME.")`
- **重复 `close()`**：幂等
- **closed 后调用 `compile`**：抛 `IllegalStateException("DynamicClassLoader has been closed")`

### 3.5 测试
- **隔离性**：两个 `DynamicClassLoader` 实例分别编译同名 `com.example.Foo`，互不冲突
- **生命周期**：`close()` 后再 `compile(...)` 抛 `IllegalStateException`
- **JAR 类路径**：`addJarPath` 之后，源码中能 `import` 那个 JAR 里的类
- **失败诊断**：语法错误时 `compile(...)` 返回 `success=false` 结果，`getDiagnostics()` 非空；`compileAndLoad(...)` 抛 `CompilationException` 且 `getResult()` 可用
- **线程安全**：10 个线程并发 `addJarPath` + `compile`，不抛 `ConcurrentModificationException` / `LinkageError`

## 四、Aspect 包设计

### 4.1 命名变化
| 1.x | 2.0 |
| --- | --- |
| `IAspect` | `IAdvice` |
| `SimpleAspect` | `SimpleAdvice` |
| `DynamicAspect` | `DynamicProxy` |
| `AspectHandler` (CGLIB `MethodInterceptor`) | `AdviceInterceptor` (ByteBuddy `@RuntimeType` 适配) |

`IAdvice` 接口保持三方法不变：`before / after / afterThrow`。

### 4.2 DynamicProxy API
```java
public final class DynamicProxy {

    public static <T> T proxy(T target, IAdvice advice);
    public static <T> T proxy(Class<T> type, IAdvice advice);
    public static <T> T proxy(Class<T> type, IAdvice advice, ClassLoader loader);
}
```

### 4.3 ByteBuddy 实现
```java
public class DynamicProxy {
    public static <T> T proxy(Class<T> type, IAdvice advice, ClassLoader loader) {
        return new ByteBuddy()
            .subclass(type)
            .method(ElementMatchers.any())
            .intercept(MethodDelegation.to(new AdviceInterceptor(advice)))
            .make()
            .load(loader, ClassLoadingStrategy.Default.INJECTION)
            .getLoaded()
            .getDeclaredConstructor()
            .newInstance();
    }
}
```

```java
public class AdviceInterceptor {
    private final IAdvice advice;

    public AdviceInterceptor(IAdvice advice) {
        this.advice = advice;
    }

    @RuntimeType
    public Object intercept(@This Object self,
                            @Origin Method method,
                            @AllArguments Object[] args,
                            @SuperCall Callable<?> superCall) throws Exception {
        advice.before(self, method, args);
        try {
            Object result = superCall.call();
            advice.after(self, method, args, result);
            return result;
        } catch (Throwable t) {
            advice.afterThrow(self, method, args, t);
            throw t;
        }
    }
}
```

`ClassLoadingStrategy.Default.INJECTION` —— 代理类注入到目标类同一 ClassLoader，框架反射（如 Spring）能正常找到代理类。

### 4.4 SimpleAdvice（修线程安全）

```java
@Slf4j
public class SimpleAdvice implements IAdvice {

    private final ThreadLocal<StopWatch> timer = new ThreadLocal<>();

    @Override
    public void before(Object target, Method method, Object[] args) {
        StopWatch sw = new StopWatch(target.getClass().getName() + "#" + method.getName());
        sw.start();
        timer.set(sw);
        log.info("SimpleAdvice before {}", sw.getId());
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();
        log.info("SimpleAdvice after {}", sw.getId());
        log.info(sw.shortSummary());
        timer.remove();
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();
        log.info("SimpleAdvice afterThrow {} {}", sw.getId(), cause.getMessage());
        log.info(sw.shortSummary());
        timer.remove();
    }
}
```

**已知限制**（Javadoc 标注）：`SimpleAdvice` 不支持**同线程的 re-entrant 调用**——同一线程在 `before→after` 之间再次进入任何用 `SimpleAdvice` 代理的方法，内层会覆盖外层的 StopWatch。本版本不做栈式 StopWatch。

### 4.5 错误处理
- 目标类无无参构造 → `ProxyCreationException`（新增）
- 被代理方法抛异常 → 透传给 `IAdvice.afterThrow` 再重抛

### 4.6 测试
1. **线程安全**：100 线程 × 1000 次 `proxy(MyService.class, new SimpleAdvice()).doSomething()`，每个 `before` 对应一次 `after`，零异常
2. **异常路径**：target method 抛异常 → `afterThrow` 调用一次，异常被重新抛出
3. **方法委托**：`@SuperCall` 真的调用到原方法
4. **自定义 advice**：实现计数 `IAdvice`，验证 before/after/afterThrow 计数正确
5. **SimpleAdvice 不复用 state**：两个不同 `DynamicProxy` 共享同一 `SimpleAdvice` 时互不干扰

## 五、Bean 包设计

### 5.1 DynamicRequestMappingHandlerMapping
直接继承 Spring 自己的 `RequestMappingHandlerMapping`，把反射访问的 protected 方法提升为 public：

```java
public class DynamicRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    public void registerHandler(Object handler) {
        detectHandlerMethods(handler);
    }

    public void unregisterHandler(Object handler) {
        Class<?> handlerType = handler.getClass();
        ReflectionUtils.doWithMethods(handlerType, method -> {
            Method mostSpecific = ClassUtils.getMostSpecificMethod(method, handlerType);
            RequestMappingInfo info = getMappingForMethod(mostSpecific, handlerType);
            if (info != null) {
                unregisterMapping(info);
            }
        });
    }
}
```

唯一反射使用：`ReflectionUtils.doWithMethods`（Spring 公开 API 工具），**完全去掉 `setAccessible(true)` + 私有方法名硬编码**。

### 5.2 DynamicBean 门面
```java
public final class DynamicBean {

    public static void registerSingleton(DefaultListableBeanFactory dbf, String beanName, Class<?> type);
    public static void unregisterSingleton(DefaultListableBeanFactory dbf, String beanName);

    public static void registerController(DefaultListableBeanFactory dbf, String beanName, Class<?> type);
    public static void unregisterController(DefaultListableBeanFactory dbf, String beanName);

    public static void refreshController(DefaultListableBeanFactory dbf, String beanName, Class<?> type);
}
```

行为变化：
- `registerController(beanName, type)` —— 新增，封装"先调用 `registerSingleton`（如果 bean 已存在则覆盖） + 再 `mapping.registerHandler` 重新探测路由"
- `unregisterController(beanName)` —— 不变，只移除路由映射，**bean 定义本身保留**
- `refreshController(beanName, type)` —— 替代原 `unregisterController(beanName, type)` 的"重注册+刷新"语义，调用 `registerSingleton` + `mapping.registerHandler`
- 全部通过 `dbf.getBean(DynamicRequestMappingHandlerMapping.class)` 拿 mapping，不再用 `"requestMappingHandlerMapping"` 字符串硬编码

### 5.3 Auto-Configuration
用 Spring 官方推荐的 `WebMvcRegistrations` 钩子替换默认 mapping：

```java
@AutoConfiguration
public class DynamicBeanAutoConfiguration {

    @Bean
    public WebMvcRegistrations webMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new DynamicRequestMappingHandlerMapping();
            }
        };
    }
}
```

`@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 是 Spring Boot 3.x 取代 `spring.factories` 的标准做法。`spring.mvc.*` 全部属性仍生效（子类继承 setter）。

### 5.4 错误处理
- `dbf.getBean(DynamicRequestMappingHandlerMapping.class)` 找不到 → 抛 `IllegalStateException("DynamicRequestMappingHandlerMapping bean not found. Did you enable DynamicBeanAutoConfiguration?")`，cause 是 `NoSuchBeanDefinitionException`
- 多次 `unregisterController` 同一 bean 的两层语义：
  - **映射层幂等**：`mapping.unregisterMapping(info)` 对不存在的 mapping 是 no-op，重复调用安全
  - **Bean 层非幂等**：`dbf.getBean(beanName)` 在 bean 已销毁（`unregisterSingleton` 之后）时会抛 `NoSuchBeanDefinitionException`。所以 `unregisterController` 调用前应确认 bean 仍存在；如需彻底清理，**先调 `unregisterController` 再 `unregisterSingleton`**，顺序反了会找不到 bean

### 5.5 测试
1. **无反射断言**：扫 `src/main` 下 `bean/` 包的所有 Java 文件，`setAccessible` 必须 0 个匹配
2. **子类 API 直接单测**：`new DynamicRequestMappingHandlerMapping()` 构造 OK，`registerHandler / unregisterHandler` 是 public
3. **集成测试**（`@SpringBootTest` + `MockMvc`）：
   - 启动时 `@RestController` `/api/hello` 返回 200
   - `DynamicBean.unregisterController(ctx, "helloCtrl")` 后 `/api/hello` 返 404
   - `DynamicBean.registerController(ctx, "helloCtrl", NewType.class)` 后 `/api/hello` 返 200
4. **Auto-config 验证**：`assertThat(ctx.getBean(RequestMappingHandlerMapping.class)).isInstanceOf(DynamicRequestMappingHandlerMapping.class)`
5. **spring.mvc 属性传递**：用 `@TestPropertySource(properties = "spring.mvc.throw-exception-if-no-handler-found=true")` 验证属性生效

## 六、DynamicRuntime 高级门面

```java
public final class DynamicRuntime implements AutoCloseable {

    public static DynamicRuntime create();
    public static DynamicRuntime create(ClassLoader parent);
    public static DynamicRuntime withBeanFactory(DefaultListableBeanFactory bf);
    public static DynamicRuntime withBeanFactory(DefaultListableBeanFactory bf, ClassLoader parent);

    public Class<?> compileAndLoad(String sourceCode);
    public Class<?> compileAndLoad(String sourceCode, CompilerOptions opts);

    public DynamicClassLoader getClassLoader();

    public void addJarPath(String jarPath);

    public void registerBean(String beanName, Class<?> type);
    public void unregisterBean(String beanName);
    public void registerController(String beanName, Class<?> type);
    public void unregisterController(String beanName);
    public void refreshController(String beanName, Class<?> type);

    @Override
    public void close();
}
```

**`close()` 语义**：只释放字节码缓存和 ClassLoader。已注册的 Bean 定义保留——属于 Spring 容器生命周期，调用方自行控制。Javadoc 写清楚。

## 七、依赖变化

```xml
<!-- 移除 -->
- spring-boot-starter-web           ← 替换为 spring-webmvc
- (隐含 cglib)                       ← 不再使用
- (javaparser-core 3.27)             ← 保留

<!-- 新增 -->
+ net.bytebuddy:byte-buddy:1.15.x    ← 与 Spring Boot 3.5.x 自带版本对齐
+ spring-boot-autoconfigure          ← 写 AutoConfiguration 需要
+ spring-webmvc                      ← 替代 starter-web
```

## 八、版本与构建

- `<version>2.0.0-SNAPSHOT</version>`
- Spring Boot BOM 保持 `3.5.x`（不锁小版本）
- JDK 17
- 加 `maven-jar-plugin` 配置
- 加 `maven-deploy-plugin` 配置（推到 JitPack 时用）
- 加 `jacoco-maven-plugin` 出覆盖率报告（不强制门槛）

## 九、CI：GitHub Actions

`.github/workflows/build.yml`：单 workflow，push 与 PR 都跑 `mvn verify`（含集成测试）。ubuntu-latest + temurin-17。预计 3-5 分钟/次。

## 十、测试基础设施

- JUnit 5（spring-boot-starter-test 自带）
- Mockito（同一）
- AssertJ（同一）
- 集成测试用 `@SpringBootTest` + `MockMvc`
- 集成测试直接走 `mvn verify`，不分 profile

## 十一、README 重写结构

1. 简介 + Gitee/GitHub 徽章
2. 快速开始（5 行代码示例）
3. 三个核心包（每段一段代码 + 一段说明）
4. `DynamicRuntime` 高级用法
5. Spring Boot 自动配置说明
6. **从 1.x 迁移指南**（API 变化表）
7. 生产环境 classpath 配置（保留原内容）
8. 构建/测试/CI

## 十二、迁移指南要点

| 1.x | 2.0 | 迁移说明 |
| --- | --- | --- |
| `DynamicCompiler.compileAndLoad(source)` | `runtime.compileAndLoad(source)` 或 `loader.compileAndLoad(source)` | 实例方法 |
| `DynamicCompiler.addJarPath(path)` | `runtime.addJarPath(path)` | 实例化 |
| `DynamicAspect.proxy(target, aspect)` | `DynamicProxy.proxy(target, advice)` | 类名 + 接口名 |
| `SimpleAspect` | `SimpleAdvice` | 改名 |
| `DynamicBean.unregisterController(bf, name, type)` | `DynamicBean.refreshController(bf, name, type)` | 改名 + 修正语义 |
| `ByteArrayClassLoader` 单例 | `DynamicClassLoader` 实例 | 全面无单例 |
| `CompilerRuntimeException` | `CompilationException` | 改名 |
| `BeanRuntimeException` | `BeanRegistrationException` | 改名 |

## 十三、风险与开放问题

| 风险 | 缓解 |
| --- | --- |
| ByteBuddy `INJECTION` 在某些 ClassLoader（如 OSGi）下失败 | 后续可加 `WRAPPER` 策略 + 工厂方法；本版本不处理 |
| `WebMvcRegistrations` 在 Spring Boot 某些 patch 版本行为不同 | 在 CI 用真实 `WebMvcAutoConfiguration` 流程跑集成测试，版本升级时定位 |
| SimpleAdvice 仍有 re-entrancy 限制 | Javadoc 标注；未来可换 `Deque<StopWatch>` |
| 2.0 完全不兼容 1.x | README 迁移指南；JitPack 保留 1.2.x tag 供老项目回退 |
| Spring Boot 4.x 时 `WebMvcRegistrations` 可能 deprecated | 4.x 是 1 年后的事，本版本不预设 |
