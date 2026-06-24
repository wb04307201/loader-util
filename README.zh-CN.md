# dynamo-spring dynamo-spring 工具包

> 一个用于动态加载和管理 Java 类的工具库，支持动态编译、AOP 代理、Spring Bean 管理。会话模型 ClassLoader、ByteBuddy 代理、子类化 Spring MVC、自动配置。

![Maven Central](https://img.shields.io/maven-central/v/io.github.wb04307201/dynamo-spring-spring-boot-starter?style=flat-square)
![JDK](https://img.shields.io/badge/JDK-17+-green.svg)
![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)

## 快速开始

### Maven 依赖

库分两个模块，按需选择：

```xml
<!-- Spring Boot 集成（推荐）：含 DynamoRuntime 门面 + DynamoBean + 自动配置 -->
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamo-spring-spring-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- 纯 JDK / 非 Spring：只要动态编译 + AOP 代理（不拉 spring-webmvc） -->
<!--
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamo-spring-core</artifactId>
    <version>1.3.0</version>
</dependency>
-->
```

| 模块 | 内容 | 依赖 |
| --- | --- | --- |
| `dynamo-spring-core` | `compiler` + `aspect` + `exception` | javaparser + byte-buddy + spring-core + slf4j |
| `dynamo-spring-spring-boot-starter` | 上述 + `DynamoRuntime` + `bean` 包 + 自动配置 | 上述 + spring-webmvc + spring-boot-autoconfigure |

### 5 行跑通

```java
import cn.wubo.dynamo.spring.DynamoRuntime;

try (DynamoRuntime runtime = DynamoRuntime.create()) {
    Class<?> clazz = runtime.compileAndLoad("public class A { public String hi() { return \"hi\"; } }");
    Object o = clazz.getDeclaredConstructor().newInstance();
    System.out.println(o.getClass().getMethod("hi").invoke(o));
}
```

### 包 ↔ 模块

| Java 包 | 所在模块 |
| --- | --- |
| `cn.wubo.dynamo.spring.compiler` | `core` |
| `cn.wubo.dynamo.spring.aspect` | `core` |
| `cn.wubo.dynamo.spring.exception` | `core` |
| `cn.wubo.dynamo.spring`（`DynamoRuntime`） | `spring-boot-starter` |
| `cn.wubo.dynamo.spring.bean` | `spring-boot-starter` |

## 三大能力

### 1. 动态编译（`compiler` 包）

`DynamoClassLoader` 会话模型 ClassLoader——每个实例独立持有 classpath、字节码缓存、文件管理器。

```java
import cn.wubo.dynamo.spring.compiler.CompilationResult;
import cn.wubo.dynamo.spring.compiler.CompilerOptions;
import cn.wubo.dynamo.spring.compiler.DynamoClassLoader;

try (DynamoClassLoader loader = DynamoClassLoader.create()) {
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
DynamoClassLoader loader = DynamoClassLoader.create(parent);

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
import cn.wubo.dynamo.spring.aspect.DynamoProxy;
import cn.wubo.dynamo.spring.aspect.IAdvice;
import java.lang.reflect.Method;

IAdvice advice = new IAdvice() {
    public void before(Object t, Method m, Object[] a) { /* ... */ }
    public void after(Object t, Method m, Object[] a, Object r) { /* ... */ }
    public void afterThrow(Object t, Method m, Object[] a, Throwable c) { /* ... */ }
};
MyService proxy = DynamoProxy.proxy(target, advice);
```

或用内置 `SimpleAdvice`（线程安全，基于 `ThreadLocal`）：

```java
import cn.wubo.dynamo.spring.aspect.SimpleAdvice;

MyService proxy = DynamoProxy.proxy(MyService.class, new SimpleAdvice());
```

> **已知限制**：`SimpleAdvice` 不支持同线程的 re-entrant 调用——同线程在 `before` 与 `after` 之间再次进入任何用 `SimpleAdvice` 代理的方法时，内层会覆盖外层的计时器。

### 3. Spring Bean 管理（`bean` 包）

不反射 Spring 私有 API——通过子类化 `RequestMappingHandlerMapping` 实现。

```java
import cn.wubo.dynamo.spring.bean.DynamoBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

// 注册 controller（bean 定义 + 路由）
DynamoBean.registerController(bf, "myCtrl", MyController.class);
// 注销 controller（只移除路由；bean 定义保留在容器中）
DynamoBean.unregisterController(bf, "myCtrl");
// 真正"替换"：先注销旧路由，再注册新类型
DynamoBean.unregisterController(bf, "myCtrl");
DynamoBean.registerController(bf, "myCtrl", NewType.class);
// 纯单例 Bean 注册（不涉及路由）
DynamoBean.registerSingleton(bf, "myBean", MyBean.class);
DynamoBean.unregisterSingleton(bf, "myBean");
```

> **关于 `refreshController`**：`refreshController(bf, name, type)` 等价于 `registerController`——
> 它会注册新类型的路由，但**不清理**旧路由。如需真正替换，请先 `unregisterController` 再注册。

### 4. DynamoRuntime 高级门面

把三件套粘到一个生命周期里：

```java
import cn.wubo.dynamo.spring.DynamoRuntime;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
    Class<?> controller = runtime.compileAndLoad(sourceCode);
    runtime.registerController("dynamicCtrl", controller);
    // 路由已生效
}
// ClassLoader 关闭后，Bean 仍在容器中（由 Spring 生命周期管理）

// runtime 还支持：registerBean / unregisterBean / unregisterController /
// refreshController / addJarPath / compile / compileAndLoad（与 DynamoClassLoader 同语义）
// 不带 BeanFactory 的轻量用法：
try (DynamoRuntime runtime = DynamoRuntime.create()) {
    Class<?> c = runtime.compileAndLoad(source);
}
// 或指定父 ClassLoader：
try (DynamoRuntime runtime = DynamoRuntime.create(customParent)) { ... }
```

## Spring Boot 自动配置

引入依赖后自动激活。无需 `@EnableXxx`、无需 `@Import`：

- `WebMvcRegistrations` 替换默认 `RequestMappingHandlerMapping` 为 `DynamoRequestMappingHandlerMapping`
- `spring.mvc.*` 所有属性继续生效（WebMvcRegistrations 透明替换 mapping，setter 全部继承到子类）

如果需要禁用：
```properties
spring.autoconfigure.exclude=cn.wubo.dynamo.spring.bean.DynamoBeanAutoConfiguration
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
mvn -B test -pl dynamo-spring-core -am
mvn -B verify -pl dynamo-spring-test -am
```

CI：GitHub Actions 跑在 `ubuntu-latest` + JDK 17 上，push 与 PR 都触发。

## 演示应用

`dynamo-spring-test` 模块自带一个可启动的 Spring Boot Web 应用，把三大能力 + DynamoRuntime 高级门面拼到 4 个 tab 里，浏览器里直接玩：

```bash
# 安装所有模块到本地仓库（首次需要；spring-boot:run 依赖 starter 模块的 jar）
mvn -B install -DskipTests

# 启动 demo（默认 http://localhost:8080）
mvn -B -pl dynamo-spring-test spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dnet.bytebuddy.experimental=true"
```

> `-Dnet.bytebuddy.experimental=true` 是为了在 JDK 21+ 上启用 ByteBuddy 实验性支持；不传也能跑，但 JDK 17 下没必要。

### 四个 tab 的能力

| Tab | 路径前缀 | 演示 |
| --- | --- | --- |
| 1. 动态编译 | `/api/compile/*` | 源码 → 编译 → 加载 → 实例化 → 反射调用；列已加载实例，删除可释放 ClassLoader |
| 2. AOP 拦截 | `/api/aspect/*` | 源码 → 编译 + 创建 ByteBuddy 代理；advice 选 `log` / `timing` / `throw`；调用时返回累积的 `before / after / afterThrow` 日志 |
| 3. 动态 Controller | `/api/bean/*` | 源码（`@RestController`）→ 编译 → `DynamoBean.registerController` → 浏览器立刻可路由；注销后访问 404；列表显示当前注册的 controller 及其路由 |
| 4. DynamoRuntime | `/api/runtime/*` | 开 session → session 内 compile / instantiate / invoke / registerController / unregisterController；关 session 后 Bean 仍在容器中 |

### 关键文件

```
dynamo-spring-test/src/main/
├── java/cn/wubo/dynamic/loader/utility/demo/
│   ├── DemoApp.java                    @SpringBootApplication 启动器
│   ├── InstanceRegistry.java           集中管理实例/代理/session 状态
│   ├── CompileController.java          Tab 1
│   ├── AspectController.java           Tab 2
│   ├── BeanController.java             Tab 3
│   ├── RuntimeController.java          Tab 4
│   ├── GlobalExceptionHandler.java     把 4xx/5xx 翻译成统一 JSON
│   ├── ArgsHelper.java                 反射调用辅助（findMethod / coerceArgs）
│   └── advice/
│       ├── LoggingAdvice.java          log：记录全部三阶段
│       ├── TimingAdvice.java           timing：ThreadLocal StopWatch
│       └── ThrowAdvice.java            throw：被动记录，让源方法自己抛以触发 afterThrow
└── resources/
    ├── application.yml                 server.port=8080
    └── static/
        ├── index.html                  单页 4 tab
        ├── app.js                      原生 JS（fetch）
        └── styles.css
```

### 端到端示例（curl）

```bash
# Tab 1：编译 Greeter.greet("alice") → "hi alice"
curl -X POST http://localhost:8080/api/compile/load \
  -H 'Content-Type: application/json' \
  -d '{"source":"public class G { public String greet(String n){return \"hi \"+n;} }"}'
# 返回 {"id":"...","className":"G","methods":[...]}

curl -X POST http://localhost:8080/api/compile/invoke \
  -H 'Content-Type: application/json' \
  -d '{"id":"...","methodName":"greet","args":["alice"]}'
# 返回 {"result":"hi alice","resultType":"java.lang.String"}

# Tab 3：动态注册 controller
curl -X POST http://localhost:8080/api/bean/register \
  -H 'Content-Type: application/json' \
  -d '{"beanName":"dynCtrl","source":"@org.springframework.web.bind.annotation.RestController public class DynCtrl { @org.springframework.web.bind.annotation.GetMapping(\"/api/dyn\") public String dyn(){return \"dynamic\";} }"}'

curl http://localhost:8080/api/dyn       # → "dynamic"

curl -X POST http://localhost:8080/api/bean/unregister \
  -H 'Content-Type: application/json' -d '{"beanName":"dynCtrl"}'

curl -i http://localhost:8080/api/dyn   # → HTTP/1.1 404
```

### 与 `test-jar` 的关系

`dynamo-spring-test` 同时生成 default-jar（demo 应用，可直接 `java -jar`）和 test-jar（IT 设施，给消费方复用）。两套产物互不影响：
- 普通 `mvn install` 把 default-jar 入本地仓库；
- IT 用 `mvn verify` 走 failsafe，作用在 test-jar 之外的另一个分类下。

DemoApp 与 TestApp 是两个独立的 `@SpringBootApplication`：`DemoApp` 在 `cn.wubo.dynamo.spring.demo` 包下（main classpath，供 `spring-boot:run`），`TestApp` 在 `cn.wubo.dynamo.spring` 包下（test classpath，IT 启动器）。

## 测试覆盖维度

| 维度 | 说明 |
| --- | --- |
| **核心 API** | `DynamoRuntime` / `DynamoClassLoader` / `DynamoProxy` 主路径 |
| **并发** | 8 线程 × 20 编译并行加载；多实例隔离；compile-vs-close race |
| **生命周期** | close 幂等；close 后操作；Bean 跨 close 保留 |
| **边界 / 错误恢复** | 空源码 / 注释 / Unicode 类名 / 接口 / 枚举 / 重复注册 / 不存在注销 |
| **AOP 多场景** | afterThrow 触发；advice 隔离；ThreadLocal 跨线程 |
| **Spring 集成** | registerController 真实写入 mapping；unregister + register 替换 |

## 许可证

Apache License 2.0
