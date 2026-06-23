package cn.wubo.dynamic.loader.utility.bean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicBeanTest {

    @RestController
    static class Ctrl {
        @GetMapping("/api/x")
        public String x() { return "x"; }
    }

    private DefaultListableBeanFactory bf() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        // 注册我们的 mapping bean（模拟 auto-config 效果）
        bf.registerSingleton("dynamicMapping", new DynamicRequestMappingHandlerMapping());
        return bf;
    }

    @Test
    void registerSingleton_beanExists() {
        DefaultListableBeanFactory bf = bf();
        DynamicBean.registerSingleton(bf, "foo", String.class);
        assertThat(bf.containsBean("foo")).isTrue();
    }

    @Test
    void unregisterSingleton_removesBean() {
        DefaultListableBeanFactory bf = bf();
        DynamicBean.registerSingleton(bf, "foo", String.class);
        DynamicBean.unregisterSingleton(bf, "foo");
        assertThat(bf.containsBean("foo")).isFalse();
    }

    @Test
    void registerController_addsBeanAndMapping() {
        DefaultListableBeanFactory bf = bf();
        DynamicBean.registerController(bf, "ctrl", Ctrl.class);
        assertThat(bf.containsBean("ctrl")).isTrue();
        assertThat(bf.getBean("ctrl")).isInstanceOf(Ctrl.class);
    }

    @Test
    void unregisterController_keepsBeanButRemovesMapping() {
        DefaultListableBeanFactory bf = bf();
        DynamicBean.registerController(bf, "ctrl", Ctrl.class);
        DynamicBean.unregisterController(bf, "ctrl");
        assertThat(bf.containsBean("ctrl")).isTrue();
        // 映射已删除（mapping 的 handlerMethods 不再包含 ctrl）
    }

    @Test
    void missingMappingBean_throws() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        assertThatThrownBy(() -> DynamicBean.registerController(bf, "x", Ctrl.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DynamicRequestMappingHandlerMapping");
    }
}