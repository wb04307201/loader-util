package cn.wubo.dynamo.spring.bean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 spring.mvc.* 属性确实被 Spring Boot 读取并应用到容器中的
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
 * （即 {@link DynamoRequestMappingHandlerMapping}）。
 *
 * <p>spec §5.5 的强制约束。因为我们用 {@code WebMvcRegistrations} 替换默认 mapping，
 * 必须确认属性 setter 仍然继承——本测试断言
 * {@link WebMvcProperties} 的字段值与外部属性一致：
 * <ul>
 *   <li>外部设 {@code spring.mvc.pathmatch.matching-strategy=path-pattern-parser}
 *   <li>Spring Boot 把 {@code WebMvcAutoConfiguration} 的 setter 调到我们的子类
 *   <li>{@code WebMvcProperties.getPathmatch().getMatchingStrategy()} 应为 {@code PATH_PATTERN_PARSER}
 * </ul>
 *
 * <p>注：{@code spring.mvc.throw-exception-if-no-handler-found} 在 Spring Boot 3.x
 * 中已从 {@code WebMvcProperties} 移除（Spring 6+ 改抛 {@code NoResourceFoundException}），
 * 故换用 {@code matchingStrategy} 作为可验证的属性。
 */
// WebEnvironment.MOCK：触发 WebMvcAutoConfiguration 以注册 WebMvcProperties Bean
// （NONE 环境 servlet 自动配置不会生效，WebMvcProperties 不会被创建）
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.mvc.pathmatch.matching-strategy=path-pattern-parser")
class DynamoBeanPropertiesIT {

    @Autowired WebMvcProperties webMvcProperties;

    @Test
    void pathmatchMatchingStrategy_propertyAppliedToWebMvcProperties() {
        assertThat(webMvcProperties.getPathmatch().getMatchingStrategy())
            .isEqualTo(WebMvcProperties.MatchingStrategy.PATH_PATTERN_PARSER);
    }
}
