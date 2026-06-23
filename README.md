# Dynamic Loader Utility 动态加载器工具包

> 一个用于动态加载和管理 Java 类的工具库，支持动态编译、AOP 代理、Spring Bean 管理。会话模型 ClassLoader、ByteBuddy 代理、子类化 Spring MVC、自动配置。

[![](https://jitpack.io/v/io.github.wb04307201/dynamic-loader-utility.svg)](https://jitpack.io/#io.github.wb04307201/dynamic-loader-utility)
![MIT](https://img.shields.io/badge/License-Apache2.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-17+-green.svg)
![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)

## 快速开始

### Maven 依赖

库分两个模块，按需选择：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<!-- Spring Boot 集成（推荐）：含 DynamicRuntime 门面 + DynamicBean + 自动配置 -->
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamic-loader-utility-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 纯 JDK / 非 Spring：只要动态编译 + AOP 代理（不拉 spring-webmvc） -->
<!--
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamic-loader-utility-core</artifactId>
    <version>1.0.0</version>
</dependency>
-->
```

| 模块 | 内容 | 依赖 |
| --- | --- | --- |
| `dynamic-loader-utility-core` | `compiler` + `aspect` + `exception` | javaparser + byte-buddy + spring-core + slf4j |
| `dynamic-loader-utility-spring-boot-starter` | 上述 + `DynamicRuntime` + `bean` 包 + 自动配置 | 上述 + spring-webmvc + spring-boot-autoconfigure |

### 5 行跑通

```java
import cn.wubo.dynamic.loader.utility.DynamicRuntime;

try (DynamicRuntime runtime = DynamicRuntime.create()) {
    Class<?> clazz = runtime.compileAndLoad("public class A { public String hi() { return \"hi\"; } }");
    Object o = clazz.getDeclaredConstructor().newInstance();
    System.out.println(o.getClass().getMethod("hi").invoke(o));
}
```

### 包 ↔ 模块

| Java 包 | 所在模块 |
| --- | --- |
| `cn.wubo.dynamic.loader.utility.compiler` | `core` |
| `cn.wubo.dynamic.loader.utility.aspect` | `core` |
| `cn.wubo.dynamic.loader.utility.exception` | `core` |
| `cn.wubo.dynamic.loader.utility`（`DynamicRuntime`） | `spring-boot-starter` |
| `cn.wubo.dynamic.loader.utility.bean` | `spring-boot-starter` |

## 三大能力

### 1. 动态编译（`compiler` 包）

`DynamicClassLoader` 会话模型 ClassLoader——每个实例独立持有 classpath、字节码缓存、文件管理器。

```java
import cn.wubo.dynamic.loader.utility.compiler.CompilationResult;
import cn.wubo.dynamic.loader.utility.compiler.CompilerOptions;
import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;

try (DynamicClassLoader loader = DynamicClassLoader.create()) {
    CompilationResult result = loader.compile(sourceCode);
    if (result.isSuccess()) {
        Class<?> clazz = result.getCompiledClass();
        // ...
    } else {
        result.getDiagnostics().forEach(System.err::println);
    }
}
```

新增选项：
```java
loader.compile(source, CompilerOptions.create()
    .sourceVersion("17")
    .targetVersion("17")
    .classpath("/path/to/lib.jar")
    .enablePreview());
```

更多 overload：
```java
// 指定父 ClassLoader（用于在隔离环境加载 JDK 自带类之外的类）
ClassLoader parent = Thread.currentThread().getContextClassLoader();
DynamicClassLoader loader = DynamicClassLoader.create(parent);

// 直接喂字节码（不经过源码编译）
Class<?> clazz = loader.defineClass("com.example.Precompiled", bytes);

// 批量添加 JAR
loader.addJarPaths("/path/a.jar", "/path/b.jar");

// 编译失败抛 CompilationException（携带 CompilationResult）
try {
    loader.compileAndLoad(badSource);
} catch (CompilationException ex) {
    CompilationResult r = ex.getResult();   // 结构化诊断
}
```

### 2. AOP 代理（`aspect` 包）

基于 ByteBuddy 的代理。`IAdvice` 三方法切面接口。

```java
import cn.wubo.dynamic.loader.utility.aspect.DynamicProxy;
import cn.wubo.dynamic.loader.utility.aspect.IAdvice;
import java.lang.reflect.Method;

IAdvice advice = new IAdvice() {
    public void before(Object t, Method m, Object[] a) { /* ... */ }
    public void after(Object t, Method m, Object[] a, Object r) { /* ... */ }
    public void afterThrow(Object t, Method m, Object[] a, Throwable c) { /* ... */ }
};
MyService proxy = DynamicProxy.proxy(target, advice);
```

或用内置 `SimpleAdvice`（线程安全，基于 `ThreadLocal`）：

```java
import cn.wubo.dynamic.loader.utility.aspect.SimpleAdvice;

MyService proxy = DynamicProxy.proxy(MyService.class, new SimpleAdvice());
```

> **已知限制**：`SimpleAdvice` 不支持同线程的 re-entrant 调用——同线程在 `before` 与 `after` 之间再次进入任何用 `SimpleAdvice` 代理的方法时，内层会覆盖外层的计时器。

### 3. Spring Bean 管理（`bean` 包）

不反射 Spring 私有 API——通过子类化 `RequestMappingHandlerMapping` 实现。

```java
import cn.wubo.dynamic.loader.utility.bean.DynamicBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

// 注册 controller（bean 定义 + 路由）
DynamicBean.registerController(bf, "myCtrl", MyController.class);
// 注销 controller（只移除路由；bean 定义保留在容器中）
DynamicBean.unregisterController(bf, "myCtrl");
// 真正"替换"：先注销旧路由，再注册新类型
DynamicBean.unregisterController(bf, "myCtrl");
DynamicBean.registerController(bf, "myCtrl", NewType.class);
// 纯单例 Bean 注册（不涉及路由）
DynamicBean.registerSingleton(bf, "myBean", MyBean.class);
DynamicBean.unregisterSingleton(bf, "myBean");
```

> **关于 `refreshController`**：`refreshController(bf, name, type)` 等价于 `registerController`——
> 它会注册新类型的路由，但**不清理**旧路由。如需真正替换，请先 `unregisterController` 再注册。

### 4. DynamicRuntime 高级门面

把三件套粘到一个生命周期里：

```java
import cn.wubo.dynamic.loader.utility.DynamicRuntime;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
    Class<?> controller = runtime.compileAndLoad(sourceCode);
    runtime.registerController("dynamicCtrl", controller);
    // 路由已生效
}
// ClassLoader 关闭后，Bean 仍在容器中（由 Spring 生命周期管理）

// runtime 还支持：registerBean / unregisterBean / unregisterController /
// refreshController / addJarPath / compile / compileAndLoad（与 DynamicClassLoader 同语义）
// 不带 BeanFactory 的轻量用法：
try (DynamicRuntime runtime = DynamicRuntime.create()) {
    Class<?> c = runtime.compileAndLoad(source);
}
// 或指定父 ClassLoader：
try (DynamicRuntime runtime = DynamicRuntime.create(customParent)) { ... }
```

## Spring Boot 自动配置

引入依赖后自动激活。无需 `@EnableXxx`、无需 `@Import`：

- `WebMvcRegistrations` 替换默认 `RequestMappingHandlerMapping` 为 `DynamicRequestMappingHandlerMapping`
- `spring.mvc.*` 所有属性继续生效（WebMvcRegistrations 透明替换 mapping，setter 全部继承到子类）

如果需要禁用：
```properties
spring.autoconfigure.exclude=cn.wubo.dynamic.loader.utility.bean.DynamicBeanAutoConfiguration
```

## 生产环境 classpath 配置

因为本地和服务器的 classpath 路径差异，服务上动态编译可能找不到 import 的类。
请在消费方项目的 `pom.xml` 加：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <configuration>
        <archive>
            <manifest>
                <addClasspath>true</addClasspath>
                <classpathPrefix>lib/</classpathPrefix>
            </manifest>
        </archive>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-dependencies</id>
            <phase>package</phase>
            <goals><goal>copy-dependencies</goal></goals>
            <configuration>
                <outputDirectory>${project.build.directory}/lib</outputDirectory>
                <stripVersion>false</stripVersion>
                <includeScope>runtime</includeScope>
            </configuration>
        </execution>
    </executions>
</plugin>
```

启动时：

```shell
java -jar -Dloader.path=lib/ your-app.jar
```

## 构建、测试、CI

仓库根目录是 parent pom，所有命令在根目录跑：

```bash
mvn -B test       # 全部模块的单元测试（113 个，分布在 core + starter）
mvn -B verify     # 单元 + 集成测试（共 123 个 = 113 unit + 10 IT，JaCoCo 报告生成到各模块 target/site/jacoco/）

# 单独跑某个模块
mvn -B test -pl dynamic-loader-utility-core -am
mvn -B verify -pl dynamic-loader-utility-test -am
```

CI：GitHub Actions 跑在 `ubuntu-latest` + JDK 17 上，push 与 PR 都触发。

## 测试覆盖维度

| 维度 | 说明 |
| --- | --- |
| **核心 API** | `DynamicRuntime` / `DynamicClassLoader` / `DynamicProxy` 主路径 |
| **并发** | 8 线程 × 20 编译并行加载；多实例隔离；compile-vs-close race |
| **生命周期** | close 幂等；close 后操作；Bean 跨 close 保留 |
| **边界 / 错误恢复** | 空源码 / 注释 / Unicode 类名 / 接口 / 枚举 / 重复注册 / 不存在注销 |
| **AOP 多场景** | afterThrow 触发；advice 隔离；ThreadLocal 跨线程 |
| **Spring 集成** | registerController 真实写入 mapping；unregister + register 替换 |

## 许可证

Apache License 2.0
