# dynamo-spring

> A Java toolkit for dynamically loading and managing Java classes — dynamic compilation, AOP proxies, and Spring Bean management. Session-scoped ClassLoader, ByteBuddy proxies, subclassed Spring MVC, and auto-configuration.

![Maven Central](https://img.shields.io/maven-central/v/io.github.wb04307201/dynamo-spring-spring-boot-starter?style=flat-square)
![JDK](https://img.shields.io/badge/JDK-17+-green.svg)
![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)

## Quick Start

### Maven Dependency

The library is split into two modules — pick what you need:

```xml
<!-- Spring Boot integration (recommended): includes the DynamoRuntime facade + DynamoBean + auto-configuration -->
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamo-spring-spring-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- Pure JDK / non-Spring: dynamic compilation + AOP proxies only (no spring-webmvc) -->
<!--
<dependency>
    <groupId>io.github.wb04307201</groupId>
    <artifactId>dynamo-spring-core</artifactId>
    <version>1.3.0</version>
</dependency>
-->
```

| Module | Contents | Dependencies |
| --- | --- | --- |
| `dynamo-spring-core` | `compiler` + `aspect` + `exception` | javaparser + byte-buddy + spring-core + slf4j |
| `dynamo-spring-spring-boot-starter` | Above + `DynamoRuntime` + `bean` package + auto-configuration | Above + spring-webmvc + spring-boot-autoconfigure |

### 5 Lines to Running

```java
import cn.wubo.dynamo.spring.DynamoRuntime;

try (DynamoRuntime runtime = DynamoRuntime.create()) {
    Class<?> clazz = runtime.compileAndLoad("public class A { public String hi() { return \"hi\"; } }");
    Object o = clazz.getDeclaredConstructor().newInstance();
    System.out.println(o.getClass().getMethod("hi").invoke(o));
}
```

### Package ↔ Module

| Java Package | Module |
| --- | --- |
| `cn.wubo.dynamo.spring.compiler` | `core` |
| `cn.wubo.dynamo.spring.aspect` | `core` |
| `cn.wubo.dynamo.spring.exception` | `core` |
| `cn.wubo.dynamo.spring` (`DynamoRuntime`) | `spring-boot-starter` |
| `cn.wubo.dynamo.spring.bean` | `spring-boot-starter` |

## Three Core Capabilities

### 1. Dynamic Compilation (`compiler` package)

`DynamoClassLoader` is a session-scoped ClassLoader — each instance independently holds its own classpath, bytecode cache, and file manager.

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

Extra options:
```java
loader.compile(source, CompilerOptions.create()
    .sourceVersion("17")
    .targetVersion("17")
    .classpath("/path/to/lib.jar")
    .enablePreview());
```

More overloads:
```java
// Specify a parent ClassLoader (for loading classes beyond JDK's built-ins in isolated environments)
ClassLoader parent = Thread.currentThread().getContextClassLoader();
DynamoClassLoader loader = DynamoClassLoader.create(parent);

// Feed bytecode directly (skip source compilation)
Class<?> clazz = loader.defineClass("com.example.Precompiled", bytes);

// Add multiple JARs at once
loader.addJarPaths("/path/a.jar", "/path/b.jar");

// Throw CompilationException on failure (carries a CompilationResult)
try {
    loader.compileAndLoad(badSource);
} catch (CompilationException ex) {
    CompilationResult r = ex.getResult();   // structured diagnostics
}
```

### 2. AOP Proxies (`aspect` package)

ByteBuddy-based proxies with a three-method `IAdvice` interception interface.

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

Or use the built-in `SimpleAdvice` (thread-safe via `ThreadLocal`):

```java
import cn.wubo.dynamo.spring.aspect.SimpleAdvice;

MyService proxy = DynamoProxy.proxy(MyService.class, new SimpleAdvice());
```

> **Known limitation**: `SimpleAdvice` does not support re-entrant calls on the same thread — if a thread re-enters any `SimpleAdvice`-proxied method between `before` and `after`, the inner call overwrites the outer call's timer.

### 3. Spring Bean Management (`bean` package)

No reflection on Spring private APIs — implemented by subclassing `RequestMappingHandlerMapping`.

```java
import cn.wubo.dynamo.spring.bean.DynamoBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();

// Register a controller (bean definition + route)
DynamoBean.registerController(bf, "myCtrl", MyController.class);
// Unregister a controller (only removes the route; bean definition remains in the container)
DynamoBean.unregisterController(bf, "myCtrl");
// To truly "replace": unregister the old route first, then register the new type
DynamoBean.unregisterController(bf, "myCtrl");
DynamoBean.registerController(bf, "myCtrl", NewType.class);
// Plain singleton Bean registration (no routes involved)
DynamoBean.registerSingleton(bf, "myBean", MyBean.class);
DynamoBean.unregisterSingleton(bf, "myBean");
```

> **About `refreshController`**: `refreshController(bf, name, type)` is equivalent to `registerController` —
> it registers the new type's route but does **not** clear the old route. To truly replace, `unregisterController` first, then register.

### 4. DynamoRuntime High-Level Facade

Glues all three capabilities together into a single lifecycle:

```java
import cn.wubo.dynamo.spring.DynamoRuntime;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
try (DynamoRuntime runtime = DynamoRuntime.withBeanFactory(bf)) {
    Class<?> controller = runtime.compileAndLoad(sourceCode);
    runtime.registerController("dynamicCtrl", controller);
    // The route is now live
}
// After the ClassLoader closes, the Bean remains in the container (managed by Spring lifecycle)

// runtime also supports: registerBean / unregisterBean / unregisterController /
// refreshController / addJarPath / compile / compileAndLoad (same semantics as DynamoClassLoader)
// Lightweight usage without a BeanFactory:
try (DynamoRuntime runtime = DynamoRuntime.create()) {
    Class<?> c = runtime.compileAndLoad(source);
}
// Or specify a parent ClassLoader:
try (DynamoRuntime runtime = DynamoRuntime.create(customParent)) { ... }
```

## Spring Boot Auto-Configuration

Activated automatically once the dependency is on the classpath. No `@EnableXxx`, no `@Import` needed:

- `WebMvcRegistrations` replaces the default `RequestMappingHandlerMapping` with `DynamoRequestMappingHandlerMapping`
- All `spring.mvc.*` properties continue to work (`WebMvcRegistrations` transparently replaces the mapping; all setters are inherited by the subclass)

To disable:
```properties
spring.autoconfigure.exclude=cn.wubo.dynamo.spring.bean.DynamoBeanAutoConfiguration
```

## Production-Classpath Configuration

Because local and server classpath paths differ, dynamic compilation on the server may fail to resolve `import`ed classes. Add the following to your consumer project's `pom.xml`:

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

At startup:

```shell
java -jar -Dloader.path=lib/ your-app.jar
```

## Build, Test, CI

The repository root is the parent POM — run all commands from the root:

```bash
mvn -B test       # Unit tests for all modules (113, distributed across core + starter)
mvn -B verify     # Unit + integration tests (123 total = 113 unit + 10 IT; JaCoCo report generated to each module's target/site/jacoco/)

# Run a single module
mvn -B test -pl dynamo-spring-core -am
mvn -B verify -pl dynamo-spring-test -am
```

CI: GitHub Actions runs on `ubuntu-latest` with JDK 17, triggered on push and PR.

## Demo Application

The `dynamo-spring-test` module ships with a runnable Spring Boot web application that combines the three core capabilities and the `DynamoRuntime` high-level facade into 4 tabs — play with it directly in your browser:

```bash
# Install all modules to the local repository (required first time; spring-boot:run depends on the starter jar)
mvn -B install -DskipTests

# Start the demo (default http://localhost:8080)
mvn -B -pl dynamo-spring-test spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dnet.bytebuddy.experimental=true"
```

> `-Dnet.bytebuddy.experimental=true` enables ByteBuddy's experimental support on JDK 21+. It is not required on JDK 17.

### The Four Tabs

| Tab | Path Prefix | Demonstration |
| --- | --- | --- |
| 1. Dynamic Compilation | `/api/compile/*` | Source → compile → load → instantiate → reflective call; list loaded instances, delete to free the ClassLoader |
| 2. AOP Interception | `/api/aspect/*` | Source → compile + create ByteBuddy proxy; advice selector: `log` / `timing` / `throw`; on call, returns accumulated `before / after / afterThrow` logs |
| 3. Dynamic Controller | `/api/bean/*` | Source (`@RestController`) → compile → `DynamoBean.registerController` → instantly routable in browser; 404 after unregister; list shows currently registered controllers and their routes |
| 4. DynamoRuntime | `/api/runtime/*` | Open a session → within the session, compile / instantiate / invoke / registerController / unregisterController; Beans persist in the container after the session closes |

### Key Files

```
dynamo-spring-test/src/main/
├── java/cn/wubo/dynamic/loader/utility/demo/
│   ├── DemoApp.java                    @SpringBootApplication launcher
│   ├── InstanceRegistry.java           Centralized instance/proxy/session state
│   ├── CompileController.java          Tab 1
│   ├── AspectController.java           Tab 2
│   ├── BeanController.java             Tab 3
│   ├── RuntimeController.java          Tab 4
│   ├── GlobalExceptionHandler.java     Translates 4xx/5xx into unified JSON
│   ├── ArgsHelper.java                 Reflection invocation helpers (findMethod / coerceArgs)
│   └── advice/
│       ├── LoggingAdvice.java          log: records all three phases
│       ├── TimingAdvice.java           timing: ThreadLocal StopWatch
│       └── ThrowAdvice.java            throw: passively records; lets the source method itself throw to trigger afterThrow
└── resources/
    ├── application.yml                 server.port=8080
    └── static/
        ├── index.html                  Single-page 4-tab UI
        ├── app.js                      Vanilla JS (fetch)
        └── styles.css
```

### End-to-End Examples (curl)

```bash
# Tab 1: Compile Greeter.greet("alice") → "hi alice"
curl -X POST http://localhost:8080/api/compile/load \
  -H 'Content-Type: application/json' \
  -d '{"source":"public class G { public String greet(String n){return \"hi \"+n;} }"}'
# Returns {"id":"...","className":"G","methods":[...]}

curl -X POST http://localhost:8080/api/compile/invoke \
  -H 'Content-Type: application/json' \
  -d '{"id":"...","methodName":"greet","args":["alice"]}'
# Returns {"result":"hi alice","resultType":"java.lang.String"}

# Tab 3: Register a controller dynamically
curl -X POST http://localhost:8080/api/bean/register \
  -H 'Content-Type: application/json' \
  -d '{"beanName":"dynCtrl","source":"@org.springframework.web.bind.annotation.RestController public class DynCtrl { @org.springframework.web.bind.annotation.GetMapping(\"/api/dyn\") public String dyn(){return \"dynamic\";} }"}'

curl http://localhost:8080/api/dyn       # → "dynamic"

curl -X POST http://localhost:8080/api/bean/unregister \
  -H 'Content-Type: application/json' -d '{"beanName":"dynCtrl"}'

curl -i http://localhost:8080/api/dyn   # → HTTP/1.1 404
```

### Relationship with `test-jar`

`dynamo-spring-test` produces both a default-jar (the demo app, runnable via `java -jar`) and a test-jar (IT infrastructure, reusable by consumers). The two artifacts do not interfere with each other:
- A normal `mvn install` puts the default-jar into the local repository;
- IT runs via `mvn verify` through failsafe, operating on a separate classifier from the test-jar.

`DemoApp` and `TestApp` are two independent `@SpringBootApplication` classes: `DemoApp` lives under the `cn.wubo.dynamo.spring.demo` package (main classpath, used by `spring-boot:run`), while `TestApp` lives under `cn.wubo.dynamo.spring` (test classpath, IT launcher).

## Test Coverage

| Dimension | Description |
| --- | --- |
| **Core API** | `DynamoRuntime` / `DynamoClassLoader` / `DynamoProxy` main paths |
| **Concurrency** | 8 threads × 20 compilations loading in parallel; multi-instance isolation; compile-vs-close race |
| **Lifecycle** | Idempotent close; post-close operations; Beans survive close |
| **Edge / Error Recovery** | Empty source / comments / Unicode class names / interfaces / enums / duplicate registration / unregister-of-nonexistent |
| **AOP Scenarios** | afterThrow triggers; advice isolation; ThreadLocal across threads |
| **Spring Integration** | `registerController` actually writes mappings; unregister + register replacement |

## License

Apache License 2.0