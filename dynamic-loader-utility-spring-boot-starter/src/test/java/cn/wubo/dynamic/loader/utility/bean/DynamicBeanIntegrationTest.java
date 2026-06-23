package cn.wubo.dynamic.loader.utility.bean;

import cn.wubo.dynamic.loader.utility.compiler.DynamicClassLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DynamicBean + DynamicRequestMappingHandlerMapping 集成维度的单元测试。
 *
 * <p>使用最小 Spring 上下文（GenericApplicationContext）验证真实交互：
 * <ul>
 *   <li>registerController 后 handler 的路由真的被 mapping 注册
 *   <li>unregisterController 后路由被移除
 *   <li>不同 controller 类的路由共存
 * </ul>
 *
 * <p>不依赖 SpringBootTest，启动 < 1s。
 */
class DynamicBeanIntegrationTest {

    @Test
    void registerController_addsMappingToHandler() throws Exception {
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamicRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            ctx.refresh();
            ctx.getBean(DynamicRequestMappingHandlerMapping.class); // 触发初始化

            try (DynamicClassLoader cl = DynamicClassLoader.create()) {
                String src = """
                    @org.springframework.web.bind.annotation.RestController
                    public class IntCtrl {
                        @org.springframework.web.bind.annotation.GetMapping("/test/int")
                        public String hello() { return "hi"; }
                    }""";
                Class<?> ctrl = cl.compileAndLoad(src);

                // 注册前 mapping 是空的
                RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
                assertThat(mapping.getHandlerMethods()).isEmpty();

                DynamicBean.registerController(bf, "intCtrl", ctrl);

                // 注册后 mapping 有了该 handler 的方法
                Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
                assertThat(handlers).hasSize(1);
                assertThat(handlers.values().iterator().next().getMethod().getName()).isEqualTo("hello");
            }
        }
    }

    @Test
    void unregisterController_removesMapping() throws Exception {
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamicRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            ctx.refresh();
            ctx.getBean(DynamicRequestMappingHandlerMapping.class);

            try (DynamicClassLoader cl = DynamicClassLoader.create()) {
                Class<?> ctrl = cl.compileAndLoad("""
                    @org.springframework.web.bind.annotation.RestController
                    public class RmCtrl {
                        @org.springframework.web.bind.annotation.GetMapping("/rm")
                        public String x() { return "x"; }
                    }""");
                DynamicBean.registerController(bf, "rmCtrl", ctrl);

                RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
                assertThat(mapping.getHandlerMethods()).hasSize(1);

                DynamicBean.unregisterController(bf, "rmCtrl");
                // bean 定义保留（unregisterController 不删 bean 定义）
                assertThat(bf.containsBean("rmCtrl")).isTrue();
                // 路由被移除
                assertThat(mapping.getHandlerMethods()).isEmpty();
            }
        }
    }

    @Test
    void registerController_twiceWithDifferentTypes_appendsMapping() throws Exception {
        // 重新注册同名 controller 不自动清理旧路由——调用方需先 unregisterController
        // 才能真正"替换"。这里验证两套路由并存。
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamicRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            ctx.refresh();
            ctx.getBean(DynamicRequestMappingHandlerMapping.class);

            try (DynamicClassLoader cl = DynamicClassLoader.create()) {
                Class<?> ctrl1 = cl.compileAndLoad("""
                    @org.springframework.web.bind.annotation.RestController
                    public class First {
                        @org.springframework.web.bind.annotation.GetMapping("/v1")
                        public String a() { return "1"; }
                    }""");
                Class<?> ctrl2 = cl.compileAndLoad("""
                    @org.springframework.web.bind.annotation.RestController
                    public class Second {
                        @org.springframework.web.bind.annotation.GetMapping("/v2")
                        public String b() { return "2"; }
                    }""");

                DynamicBean.registerController(bf, "shared", ctrl1);
                DynamicBean.registerController(bf, "shared", ctrl2);

                RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
                Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
                // 两条路由并存：旧的没被清理
                assertThat(handlers).hasSize(2);
            }
        }
    }

    @Test
    void registerController_afterUnregister_trulyReplaces() throws Exception {
        // 显式 unregister 后再 register，旧路由被清，新路由就位。
        try (GenericApplicationContext ctx = new GenericApplicationContext()) {
            DefaultListableBeanFactory bf = ctx.getDefaultListableBeanFactory();
            GenericBeanDefinition mappingDef = new GenericBeanDefinition();
            mappingDef.setBeanClass(DynamicRequestMappingHandlerMapping.class);
            bf.registerBeanDefinition("mapping", mappingDef);
            ctx.refresh();
            ctx.getBean(DynamicRequestMappingHandlerMapping.class);

            try (DynamicClassLoader cl = DynamicClassLoader.create()) {
                Class<?> ctrl1 = cl.compileAndLoad("""
                    @org.springframework.web.bind.annotation.RestController
                    public class Rep1 {
                        @org.springframework.web.bind.annotation.GetMapping("/rep/old")
                        public String a() { return "old"; }
                    }""");
                Class<?> ctrl2 = cl.compileAndLoad("""
                    @org.springframework.web.bind.annotation.RestController
                    public class Rep2 {
                        @org.springframework.web.bind.annotation.GetMapping("/rep/new")
                        public String b() { return "new"; }
                    }""");

                DynamicBean.registerController(bf, "repCtrl", ctrl1);
                DynamicBean.unregisterController(bf, "repCtrl");
                DynamicBean.registerController(bf, "repCtrl", ctrl2);

                RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
                Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
                assertThat(handlers).hasSize(1);
                Method m = handlers.values().iterator().next().getMethod();
                assertThat(m.getDeclaringClass().getSimpleName()).isEqualTo("Rep2");
            }
        }
    }

    @Test
    void registerSingleton_thenGetBean_instantiates() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamicBean.registerSingleton(bf, "service", java.util.concurrent.atomic.AtomicLong.class);
        Object bean = bf.getBean("service");
        assertThat(bean).isInstanceOf(java.util.concurrent.atomic.AtomicLong.class);
    }

    @Test
    void unregisterSingleton_destroysInstance() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        DynamicBean.registerSingleton(bf, "service", java.util.concurrent.atomic.AtomicLong.class);
        bf.getBean("service"); // 触发实例化
        assertThat(bf.containsBean("service")).isTrue();
        DynamicBean.unregisterSingleton(bf, "service");
        assertThat(bf.containsBean("service")).isFalse();
    }
}
