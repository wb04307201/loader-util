import cn.wubo.loader.util.MethodUtils;
import cn.wubo.loader.util.SpringContextUtils;
import cn.wubo.loader.util.bean_loader.DynamicBean;
import cn.wubo.loader.util.class_loader.DynamicClass;
import cn.wubo.loader.util.jar_loader.DynamicJar;
import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

@SpringBootTest(classes = SpringContextUtils.class)
@AutoConfigureMockMvc
class DynamicTest {

    @Autowired
    MockMvc mockMvc;

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
        DynamicClass dynamicClass = DynamicClass.init(javaSourceCode, "cn.wubo.loader.util.TestClass").compiler();
        Class<?> clazz = dynamicClass.load();
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
                                
                public class TestClass {
                                
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
        DynamicClass dynamicClass = DynamicClass.init(javaSourceCode, "cn.wubo.loader.util.TestClass").compiler();
        Class<?> clazz = dynamicClass.load();
        Object obj =  MethodUtils.invokeClass(clazz, "testMethod", "world");
        Assertions.assertEquals(obj.toString(), "TestClass.User(name=world)");
    }

    @Test
    void testJarClass() {
        try (DynamicJar dynamicJar = DynamicJar.init(".\\hutool-all-5.8.29.jar")) {
            Class<?> clazz = dynamicJar.load("cn.hutool.core.util.IdUtil");
            String str = (String) MethodUtils.invokeClass(clazz, "randomUUID");
            Assertions.assertFalse(str.isEmpty());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testBean() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        String beanName = DynamicBean.init(DynamicClass.init(javaSourceCode, "cn.wubo.loader.util.TestClass")).load();
        String str = MethodUtils.invokeBean(beanName, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");
    }

    @Test
    void testAutowiredBean() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        String beanName = DynamicBean.init(DynamicClass.init(javaSourceCode, "cn.wubo.loader.util.TestClass")).load();
        String str = MethodUtils.invokeBean(beanName, "testMethod", "world");
        Assertions.assertEquals(str, "Hello,world!");

        String javaSourceCode2 = """
                package cn.wubo.loader.util;

                import org.springframework.beans.factory.annotation.Autowired;
                import cn.wubo.loader.util.TestClass;

                public class TestClass2 {
                                
                    @Autowired
                    TestClass testClass;

                    public String testMethod(String name) {
                        return testClass.testMethod(name);
                    }
                }
                """;
        String beanName2 = DynamicBean.init(DynamicClass.init(javaSourceCode2, "cn.wubo.loader.util.TestClass2")).load();
        String str2 = MethodUtils.invokeBean(beanName2, "testMethod", "world");
        Assertions.assertEquals(str2, "Hello,world!");
    }

    @Test
    void testGroovy() {
        String javaSourceCode = """
                package cn.wubo.loader.util;
                                
                public class TestClass {
                                
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
                                
                public class TestClass {
                                
                    public String testMethod(String name){
                        return String.format("Hello,%s!",name);
                    }
                }
                """;
        DynamicClass dynamicClass = DynamicClass.init(javaSourceCode, "cn.wubo.loader.util.TestClass").compiler();
        Class<?> clasz = dynamicClass.load();
        try {
            Object obj = MethodUtils.proxy(clasz.newInstance());
            String str = MethodUtils.invokeClass(obj, "testMethod", "world");
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
