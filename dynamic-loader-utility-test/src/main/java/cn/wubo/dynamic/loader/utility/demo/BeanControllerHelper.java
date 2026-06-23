package cn.wubo.dynamic.loader.utility.demo;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 反射读取指定 controller bean 的 {@code @RequestMapping} 路由列表。
 *
 * <p>BeanController 与 RuntimeController 都用此方法。
 *
 * <p>实现思路：拿 Spring 标准 {@link RequestMappingHandlerMapping} bean，调
 * {@code getHandlerMethods()} 拿到 {@code Map<RequestMappingInfo, HandlerMethod>}，
 * 过滤 {@code HandlerMethod.getBean() == bf().getBean(beanName)} 的项。
 * 反射访问 {@code HandlerMethod.getBean()} 避免编译期强依赖 Spring MVC 内部类。
 *
 * <p>注意：Spring 6 / Spring Boot 3 默认用 {@code PathPatternsRequestCondition}，
 * 旧 API {@link RequestMappingInfo#getPatternsCondition()} 在新版本中可能返回
 * {@code null}。我们改用 {@link RequestMappingInfo#getDirectPaths()} 取 URL 路径。
 */
final class BeanControllerHelper {

    private BeanControllerHelper() {}

    static List<Map<String, Object>> routesForBean(ApplicationContext ctx, String beanName) {
        DefaultListableBeanFactory dbf = (DefaultListableBeanFactory)
            ((ConfigurableApplicationContext) ctx).getBeanFactory();
        Object handler;
        try {
            handler = dbf.getBean(beanName);
        } catch (Exception e) {
            return List.of();
        }
        RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
        Map<String, Map<String, Object>> sorted = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Object hm = entry.getValue();
            Object bean;
            try {
                Method getBean = hm.getClass().getMethod("getBean");
                bean = getBean.invoke(hm);
            } catch (ReflectiveOperationException e) {
                continue;
            }
            if (bean != handler) continue;

            // Spring 6+：直接读 direct paths（字符串 Set，不依赖 PathPattern / AntPathMatcher）
            Set<String> paths = info.getDirectPaths();
            if (paths == null || paths.isEmpty()) continue;

            // 反射读 HTTP methods 列表（避免编译期强依赖 RequestMethodsRequestCondition）
            String httpMethod = extractHttpMethod(info);

            for (String p : paths) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("method", httpMethod);
                r.put("path", p);
                sorted.put(p + "|" + httpMethod, r);
            }
        }
        return new ArrayList<>(sorted.values());
    }

    /**
     * 反射读 HTTP 方法。Spring 6 中 {@code getMethodsCondition().getMethods()} 返回
     * {@code Set<RequestMethod>}，但 API 在不同小版本里有差异，所以反射更稳。
     */
    private static String extractHttpMethod(RequestMappingInfo info) {
        try {
            Method getMethodsCondition = info.getClass().getMethod("getMethodsCondition");
            Object mc = getMethodsCondition.invoke(info);
            if (mc == null) return "GET";
            Method getMethods = mc.getClass().getMethod("getMethods");
            Object methods = getMethods.invoke(mc);
            if (methods instanceof Set<?> set && !set.isEmpty()) {
                Object first = set.iterator().next();
                if (first instanceof RequestMethod rm) return rm.name();
                return first.toString();
            }
            return "GET";
        } catch (ReflectiveOperationException e) {
            return "GET";
        }
    }
}

