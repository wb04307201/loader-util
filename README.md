# loader-util 动态编译、加载、执行工具

[![](https://jitpack.io/v/com.gitee.wb04307201/loader-util.svg)](https://jitpack.io/#com.gitee.wb04307201/loader-util)

## 第一步 增加 JitPack 仓库
```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
```

## 第二步 引入jar
```xml
	<dependency>
	    <groupId>com.gitee.wb04307201</groupId>
	    <artifactId>loader-util</artifactId>
	    <version>Tag</version>
	</dependency>
```

## 第三步 如何使用
## 1. DynamicBean 动态编译加载Bean并执行

> 使用DynamicBean需要配置@ComponentScan，包括cn.wubo.loader.util.bean_loader.SpringContextUtil文件

```java
    @GetMapping(value = "/test/bean")
    public String testBean(){
        String javaSourceCode = "package cn.wubo.loader.util;\n" +
                "\n" +
                "public class TestClass {\n" +
                "    \n" +
                "    public String testMethod(String name){\n" +
                "        return String.format(\"Hello,%s!\",name);\n" +
                "    }\n" +
                "}";
        String fullClassName = "cn.wubo.loader.util.TestClass";
        String methodName = "testMethod";
        String beanName = DynamicBean.init(DynamicClass.init(javaSourceCode,fullClassName)).load();
        return (String) MethodUtils.invokeBean(beanName,methodName,"world");
    }
```

## 2. DynamicClass 动态编译加载Class并执行
```java
    @GetMapping(value = "/test/class")
    public String testClass(){
        String javaSourceCode = "package cn.wubo.loader.util;\n" +
                "\n" +
                "public class TestClass {\n" +
                "    \n" +
                "    public String testMethod(String name){\n" +
                "        return String.format(\"Hello,%s!\",name);\n" +
                "    }\n" +
                "}";
        String fullClassName = "cn.wubo.loader.util.TestClass";
        String methodName = "testMethod";

        DynamicClass dynamicClass = DynamicClass.init(javaSourceCode, fullClassName).compiler();
        return (String) MethodUtils.invokeClass(dynamicClass.load(), methodName, "world");
    }
```

## 3. DynamicJar 动态加载外部jar到项目中
> 如果动态加载bean和动态加载class执行时用到了外呼jar，可预先将jar加载到项目中
```java
    @GetMapping(value = "/test/jar")
    public String testJar(){
        Class<?> clasz = DynamicJar.init("D:\\maven-repository\\repository\\cn\\hutool\\hutool-all\\5.3.2\\hutool-all-5.3.2.jar").load("cn.hutool.core.util.IdUtil");
        return (String) MethodUtils.invokeClass(clasz, "randomUUID");
    }
```

[示例代码](https://gitee.com/wb04307201/loader-util-test)
