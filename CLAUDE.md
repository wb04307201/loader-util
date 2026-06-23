# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`dynamic-loader-utility` 是一个 Java 工具库，提供三大能力：

1. **运行时动态编译并加载 Java 类**（`compiler` 包）—— 基于 JDK 自带的 `javax.tools.JavaCompiler`，源码编译后字节码保存在内存中并通过自定义 `ClassLoader` 加载，无需落盘 `.class`。
2. **AOP 代理**（`aspect` 包）—— 基于 ByteBuddy 的方法拦截，提供前置/后置/异常三个增强点。
3. **Spring Bean 动态管理**（`bean` 包）—— 在 Spring 容器中运行时注册/注销单例 Bean，并通过子类化 `RequestMappingHandlerMapping` 动态增删 Controller 路由（不反射 Spring 私有 API）。

发布到 JitPack：依赖坐标 `io.github.wb04307201:dynamic-loader-utility-spring-boot-starter:1.0.0`（Spring 集成）或 `io.github.wb04307201:dynamic-loader-utility-core:1.0.0`（纯 JDK）。

## 模块结构（自 1.0 起拆分为 3 模块）

| 模块 | artifactId | 内容 | 主要依赖 |
| --- | --- | --- | --- |
| parent | `dynamic-loader-utility-parent` | packaging=pom，集中 BOM / pluginManagement | — |
| core | `dynamic-loader-utility-core` | `compiler` + `aspect` + `exception`（含 `SimpleAdvice`，依赖 spring-core 的 StopWatch） | javaparser + byte-buddy + spring-core + slf4j |
| starter | `dynamic-loader-utility-spring-boot-starter` | 上述 + `DynamicRuntime` 顶层门面 + `bean` 包 + `DynamicBeanAutoConfiguration` + `AutoConfiguration.imports` | core + spring-webmvc + spring-boot-autoconfigure |
| test | `dynamic-loader-utility-test` | 仅 `src/test`：`DynamicRuntimeIT` + bean 包 IT + `TestApp` 启动器。生成 `*-tests.jar` 可被消费方复用为测试基础设施 | starter（test scope） + spring-boot-starter-test + spring-boot-starter-tomcat |

**关键决策**：`SimpleAdvice` 用 `org.springframework.util.StopWatch`（spring-core 工具类），所以放 core 而不是 starter——core 因此引入 spring-core（不是 spring-webmvc）。`DynamicRuntime` 必须放 starter，因为它依赖 `DynamicBean`，而 `DynamicBean` 依赖 `DefaultListableBeanFactory`（Spring Web 模块）。

## 构建与开发

Maven 多模块项目，Java 17，Spring Boot 3.5.3（依赖管理通过 BOM 引入）。

```bash
# 全部模块编译
mvn compile

# 打包所有模块（产物在 dynamic-loader-utility-*/target/）
mvn package

# 跳过测试打包
mvn package -DskipTests

# 全量测试（113 unit + 10 IT = 123 个）
mvn -B test       # 仅单元测试
mvn -B verify     # 单元 + 集成测试 + JaCoCo 报告

# 单独跑某个模块
mvn -B test -pl dynamic-loader-utility-core -am
mvn -B verify -pl dynamic-loader-utility-test -am

# 清理
mvn clean
```

## 架构与包结构

根包：`cn.wubo.dynamic.loader.utility`

| 包 | 模块 | 职责 | 关键类 |
| --- | --- | --- | --- |
| `compiler` | core | 内存中编译 Java 源码、加载编译产物、支持外部 JAR 依赖 | `DynamicClassLoader`（实例化 ClassLoader，会话模型）、`CompilerOptions`（编译选项构建器）、`MemoryFileManager` / `MemoryJavaFileObject` / `MemoryClassFileObject`（实现 `javax.tools` 的内存文件对象） |
| `aspect` | core | ByteBuddy 方法拦截 | `DynamicProxy`（代理工厂，门面类）、`AdviceInterceptor`（`MethodDelegation` 实现）、`IAdvice`（切面接口）、`SimpleAdvice`（基于 `StopWatch` 的方法计时实现，**不支持同线程 re-entrant**） |
| `exception` | core | 自定义运行时异常 | `CompilationException`、`BeanRegistrationException`、`ProxyCreationException` |
| `bean` | starter | Spring 容器动态注册/注销 + 子类化 `RequestMappingHandlerMapping` | `DynamicBean`（静态工具类）、`DynamicRequestMappingHandlerMapping`（继承自 `RequestMappingHandlerMapping`，被子类化路径注入）、`DynamicBeanAutoConfiguration`（通过 `WebMvcRegistrations` 注册） |
| (顶层) | starter | 三件套粘合到生命周期 | `DynamicRuntime`（持有 `DynamicClassLoader` + 可选 `DefaultListableBeanFactory`，`try-with-resources` 自动 close） |

### 关键设计点

- **`DynamicClassLoader` 是实例化的**——与 2.0 之前的单例 `ByteArrayClassLoader` 不同，每次 `create()` 返回新实例，classpath/字节码缓存/文件管理器独立持有。`try-with-resources` 模式确保 close 后字节码被清理。

- **`DynamicRequestMappingHandlerMapping` 不反射 Spring 私有 API**——2.0 之前的版本通过反射调用 `getMappingForMethod`/`detectHandlerMethods`，2.0 起改为**子类化**整个 `RequestMappingHandlerMapping`，通过 `WebMvcRegistrations` 注册子类（Spring 官方推荐的"替换 MVC 组件"入口）。这样升级 Spring Boot 主版本时不需要重新验证反射点。

- **ByteBuddy 替代 CGLIB**——`net.bytebuddy` 不是 Spring 重新打包版本。注意 ByteBuddy 在 JDK 25 上需要 `-Dnet.bytebuddy.experimental=true`（已在父 pom 的 surefire/failsafe `<argLine>` 中配好）。

- **JavaParser 用于从源码字符串中解析类名**（`DynamicClassLoader.parseClassName`）。动态编译的源码必须是 JavaParser 能正确解析的合法 Java 代码——package 声明 + 第一个 `typeDeclaration` 就是要加载的 FQCN。

- **`SimpleAdvice` 的 ThreadLocal 限制**——`before/after` 通过 ThreadLocal 持有 `StopWatch`，同线程在 `before` 与 `after` 之间再次进入任何用 `SimpleAdvice` 代理的方法时，内层会覆盖外层的计时器。如果需要 re-entrant 支持，请实现自己的 `IAdvice`。

- **`refreshController` 等价于 `registerController`**——不清理旧路由，如需真正"替换"必须先 `unregisterController` 再 `registerController`。README 和测试都有警告说明。

- **编译结果默认不持久化**：所有 `.class` 都仅存在于内存中。如果需要磁盘落盘，需要自行改造 `MemoryClassFileObject`。

- **`AutoConfiguration.imports` 而非 `spring.factories`**——自动配置通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册（Spring Boot 3.x 标准），没有 `META-INF/spring.factories`。

### 核心调用链

- 动态编译加载：`DynamicRuntime.compileAndLoad(source)` → `DynamicClassLoader.compile(source)` → `parseClassName`（JavaParser）→ `compile`（`JavaCompiler` + `MemoryFileManager`）→ 字节码写入实例的 `Map<String, byte[]>` → `load(className)` 返回 `Class<?>`。
- AOP 代理：`DynamicProxy.proxy(target, advice)` → ByteBuddy `MethodDelegation` 设置父类 + `AdviceInterceptor` 回调 → 调用方法时按 `IAdvice.before → method.invoke → IAdvice.after / afterThrow` 顺序触发。
- 动态 Bean：`DynamicBean.registerController` 写 `BeanDefinition` + 调用 `DynamicRequestMappingHandlerMapping.registerMapping`；`unregisterController` 调 `unregisterMapping` + `destroySingleton`。通过子类化 `ApplicationObjectSupport` 的 `setApplicationContext` 拿到 `ApplicationContext`。

## 生产环境特别说明（README 中重要约束）

消费者在生产环境使用动态编译时，由于**本地与服务器 classpath 路径差异**导致 import 解析失败，需要在消费方项目中：

1. `maven-jar-plugin` 中开启 `addClasspath=true` 并设置 `classpathPrefix=lib/`；
2. `maven-dependency-plugin` 绑定 `package` 阶段，将 runtime 依赖拷贝到 `${project.build.directory}/lib`；
3. 启动时通过 `java -jar -Dloader.path=lib/ xxx.jar` 指定外部 jar 目录，让 `DynamicClassLoader`（继承自 `URLClassLoader`）能解析到 import 的类。

如果修改本库使其不再依赖运行时 classpath（例如把 `URLClassLoader` 替换为从应用 ClassLoader 反射查找类），上述约束可以放宽——但需保持向后兼容。

## 公共 API 速查

```java
// 编译加载（来自 core，pure JDK + JavaParser + ByteBuddy）
try (DynamicClassLoader loader = DynamicClassLoader.create()) {
    Class<?> clazz = loader.compileAndLoad(sourceCode);
    CompilationResult result = loader.compile(sourceCode, CompilerOptions.create().sourceVersion("17").targetVersion("17"));
    loader.addJarPath("/path/to/dependency.jar");
}

// AOP（来自 core）
MyService proxy = DynamicProxy.proxy(target, new SimpleAdvice());
// 或自定义：实现 IAdvice 接口

// Spring Bean（来自 starter）
DynamicBean.registerSingleton(beanFactory, "myBean", MyBeanClass.class);
DynamicBean.unregisterSingleton(beanFactory, "myBean");
DynamicBean.registerController(beanFactory, "myCtrl", MyController.class);
DynamicBean.unregisterController(beanFactory, "myCtrl");

// 门面：把三件套粘合（来自 starter）
try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(beanFactory)) {
    Class<?> controller = runtime.compileAndLoad(sourceCode);
    runtime.registerController("dynamicCtrl", controller);
}
```

## 许可证

Apache License 2.0（见 `LICENSE`）。
