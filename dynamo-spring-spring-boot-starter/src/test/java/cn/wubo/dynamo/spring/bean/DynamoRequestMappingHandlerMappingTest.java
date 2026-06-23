package cn.wubo.dynamo.spring.bean;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoRequestMappingHandlerMappingTest {

    @RestController
    static class Hello {
        @GetMapping("/hi")
        public String hi() { return "hi"; }
    }

    @Test
    void registerHandler_isPublicMethod() throws NoSuchMethodException {
        Method m = DynamoRequestMappingHandlerMapping.class.getMethod("registerHandler", Object.class);
        assertThat(Modifier.isPublic(m.getModifiers())).isTrue();
    }

    @Test
    void unregisterHandler_isPublicMethod() throws NoSuchMethodException {
        Method m = DynamoRequestMappingHandlerMapping.class.getMethod("unregisterHandler", Object.class);
        assertThat(Modifier.isPublic(m.getModifiers())).isTrue();
    }

    @Test
    void registerHandler_thenUnregisterHandler_removesMapping() {
        DynamoRequestMappingHandlerMapping mapping = new DynamoRequestMappingHandlerMapping();
        // 不调 afterPropertiesSet，直接调 registerHandler
        // 父类的 detectHandlerMethods 不依赖完整初始化
        Hello handler = new Hello();
        mapping.registerHandler(handler);
        assertThat(mapping.getHandlerMethods().values()).extracting("bean").contains(handler);

        mapping.unregisterHandler(handler);
        assertThat(mapping.getHandlerMethods()).isEmpty();
    }
}
