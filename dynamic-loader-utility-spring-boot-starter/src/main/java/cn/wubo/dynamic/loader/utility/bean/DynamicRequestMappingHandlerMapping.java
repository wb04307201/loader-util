package cn.wubo.dynamic.loader.utility.bean;

import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 在 Spring 标准 {@link RequestMappingHandlerMapping} 之上，把 protected 方法暴露为 public，
 * 让 {@link DynamicBean} 在不反射的前提下注册/注销 controller 路由。
 */
public class DynamicRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    /** 注册一个 handler 上所有 {@code @RequestMapping} 系列方法到当前 mapping。 */
    public void registerHandler(Object handler) {
        detectHandlerMethods(handler);
    }

    /**
     * 从当前 mapping 中移除该 handler 的所有路由映射。
     * bean 定义本身不会被销毁。
     */
    public void unregisterHandler(Object handler) {
        Class<?> handlerType = handler.getClass();
        ReflectionUtils.doWithMethods(handlerType, method -> {
            Method mostSpecific = ClassUtils.getMostSpecificMethod(method, handlerType);
            RequestMappingInfo info = getMappingForMethod(mostSpecific, handlerType);
            if (info != null) {
                unregisterMapping(info);
            }
        });
    }
}
