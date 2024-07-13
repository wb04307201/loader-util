import cn.wubo.loader.util.LoaderUtils;
import cn.wubo.loader.util.MethodUtils;
import cn.wubo.loader.util.SpringContextUtils;
import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest(classes = SpringContextUtils.class)
@AutoConfigureMockMvc
class LoderUtilsTest {

    @Test
    void testClass() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        LoaderUtils.compiler(javaSourceCode, "cn.wubo.loader.util.TestClass");
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass");
        String str = (String) MethodUtils.invokeClass(clazz, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");
    }

    @Test
    void testClassDelay() {
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass");
        String str = (String) MethodUtils.invokeClass(clazz, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");
    }

    @Test
    void testInnerClass() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                import lombok.AllArgsConstructor;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                                
                public class TestClass1 {
                                
                    public Object testMethod(String name){
                        return new User(name);
                    }
                    
                    @Data
                    @AllArgsConstructor
                    @NoArgsConstructor
                    public static class User {
                        private String name;
                    }
                }
                """;
        LoaderUtils.compiler(javaSourceCode, "cn.wubo.loader.util.TestClass1");
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass1");
        Object obj = MethodUtils.invokeClass(clazz, "testMethod", "world");
        Assertions.assertEquals(obj.toString(), "TestClass1.User(name=world)");
    }

    @Test
    void testJarClass() {
        LoaderUtils.addJarPath("./hutool-all-5.8.29.jar");
        Class<?> clazz = LoaderUtils.load("cn.hutool.core.util.IdUtil");
        String str = (String) MethodUtils.invokeClass(clazz, "randomUUID");
        Assertions.assertFalse(str.isEmpty());
    }

    @Test
    void testBean() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass2 {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        LoaderUtils.compiler(javaSourceCode, "cn.wubo.loader.util.TestClass2");
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass2");
        String beanName = LoaderUtils.registerSingleton(clazz);
        String str = MethodUtils.invokeBean(beanName, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");
    }

    @Test
    void testBeans() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass3 {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;

        LoaderUtils.compiler(javaSourceCode, "cn.wubo.loader.util.TestClass3");
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass3");
        String beanName = LoaderUtils.registerSingleton(clazz);
        Assertions.assertEquals(beanName, "testClass3");

        String javaSourceCode2 = """
                package cn.wubo.loader.util;
                                
                import cn.wubo.loader.util.MethodUtils;
                                
                public class TestClass4 {

                    public String testMethod(String name) {
                        return MethodUtils.invokeBean("testClass3", "testMethod", "world");
                    }
                }
                """;
        LoaderUtils.compiler(javaSourceCode2, "cn.wubo.loader.util.TestClass4");
        Class<?> clazz2 = LoaderUtils.load("cn.wubo.loader.util.TestClass4");
        String beanName2 = LoaderUtils.registerSingleton(clazz2);
        Assertions.assertEquals(beanName2, "testClass4");
        String str2 = MethodUtils.invokeBean(beanName2, "testMethod", "world");
        Assertions.assertEquals(str2, "Hello,world!");
    }

    @Test
    void testGroovy() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass5 {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            groovyClassLoader.parseClass(javaSourceCode);
            Class<?> clazz = groovyClassLoader.parseClass(javaSourceCode);
            String str = (String) MethodUtils.invokeClass(clazz, "testMethod", "world");
            Assertions.assertEquals(str, "Hello,world!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAspect() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass6 {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        LoaderUtils.compiler(javaSourceCode, "cn.wubo.loader.util.TestClass6");
        Class<?> clazz = LoaderUtils.load("cn.wubo.loader.util.TestClass6");
        try {
            Object obj = MethodUtils.proxy(clazz.newInstance());
            String str = MethodUtils.invokeClass(obj, "testMethod", "world");
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testClassOnce() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass7 {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        Class<?> clazz = LoaderUtils.compilerOnce(javaSourceCode, "cn.wubo.loader.util.TestClass7");
        String str = (String) MethodUtils.invokeClass(clazz, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");
    }
}
