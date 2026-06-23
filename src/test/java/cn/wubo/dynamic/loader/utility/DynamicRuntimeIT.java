package cn.wubo.dynamic.loader.utility;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DynamicRuntimeIT.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicRuntimeIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Autowired ApplicationContext ctx;
    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    @Test
    void compileRegisterAndHitEndpoint() throws Exception {
        DefaultListableBeanFactory bf = (DefaultListableBeanFactory)
            ((ConfigurableApplicationContext) ctx).getBeanFactory();
        String src = """
            @org.springframework.web.bind.annotation.RestController
            public class DynamicCtrl {
                @org.springframework.web.bind.annotation.GetMapping("/api/dyn")
                public String dyn() { return "dyn"; }
            }
            """;
        try (DynamicRuntime runtime = DynamicRuntime.withBeanFactory(bf)) {
            Class<?> clazz = runtime.compileAndLoad(src);
            runtime.registerController("dynamicCtrl", clazz);
        }
        assertThat(rest.getForEntity("http://localhost:" + port + "/api/dyn", String.class)
                      .getBody()).isEqualTo("dyn");
    }
}
