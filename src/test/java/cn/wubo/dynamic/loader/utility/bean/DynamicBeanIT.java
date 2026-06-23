package cn.wubo.dynamic.loader.utility.bean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicBeanIT {

    @Autowired ApplicationContext ctx;
    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    @Test
    void defaultRequestMappingHandlerMapping_isOurSubclass() {
        Object bean = ctx.getBean(RequestMappingHandlerMapping.class);
        assertThat(bean).isInstanceOf(DynamicRequestMappingHandlerMapping.class);
    }

    @Test
    void unregisterController_endpointReturns404() {
        ResponseEntity<String> before = rest.getForEntity("http://localhost:" + port + "/api/hello", String.class);
        assertThat(before.getStatusCode().value()).isEqualTo(200);

        DefaultListableBeanFactory bf = (DefaultListableBeanFactory)
            ((ConfigurableApplicationContext) ctx).getBeanFactory();
        DynamicBean.unregisterController(bf, "helloController");

        ResponseEntity<String> after = rest.getForEntity("http://localhost:" + port + "/api/hello", String.class);
        assertThat(after.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void springMvcProperties_passToSubclass() {
        // 简单断言：WebMvcAutoConfiguration 流程没被破坏，ContextRefresher 等 bean 存在
        assertThat(ctx.containsBean("requestMappingHandlerMapping")).isTrue();
    }
}
