package cn.wubo.dynamic.loader.utility.bean;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 自动配置：让 Spring MVC 使用 {@link DynamicRequestMappingHandlerMapping} 替代默认的
 * {@link RequestMappingHandlerMapping}。
 *
 * <p>使用 {@link WebMvcRegistrations} 是 Spring 官方推荐的"替换 MVC 组件"入口，
 * 不依赖 {@code @Primary} 也不需要 {@code @ConditionalOnMissingBean}。
 */
@AutoConfiguration
public class DynamicBeanAutoConfiguration {

    @org.springframework.context.annotation.Bean
    public WebMvcRegistrations dynamicWebMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new DynamicRequestMappingHandlerMapping();
            }
        };
    }
}