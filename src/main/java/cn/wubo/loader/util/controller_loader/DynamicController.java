package cn.wubo.loader.util.controller_loader;

import cn.wubo.loader.util.SpringContextUtils;
import cn.wubo.loader.util.class_loader.DynamicClass;
import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
public class DynamicController {

    private DynamicClass dynamicClass;

    public DynamicController(DynamicClass dynamicClass) {
        this.dynamicClass = dynamicClass;
    }

    public static DynamicController init(DynamicClass dynamicClass) {
        log.debug("初始化controller fullClassName:{}", dynamicClass.getFullClassName());
        return new DynamicController(dynamicClass);
    }

    /**
     * 加载方法
     * @return bean名称
     */
    public String load() {
        // 获取bean名称
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        // 获取RequestMappingHandlerMapping实例
        RequestMappingHandlerMapping requestMappingHandlerMapping = SpringContextUtils.getBean("requestMappingHandlerMapping");
        // 加载类
        Class<?> type = dynamicClass.compiler().load();
        // 遍历类的方法
        ReflectionUtils.doWithMethods(type, method -> {
            // 获取最具体的实现方法
            Method mostSpecificMethod = ClassUtils.getMostSpecificMethod(method, type);
            try {
                // 获取RequestMappingInfo实例
                Method declaredMethod = requestMappingHandlerMapping.getClass().getDeclaredMethod("getMappingForMethod", Method.class, Class.class);
                declaredMethod.setAccessible(true);
                RequestMappingInfo requestMappingInfo = (RequestMappingInfo) declaredMethod.invoke(requestMappingHandlerMapping, mostSpecificMethod, type);
                // 如果RequestMappingInfo不为空，则注销对应的映射
                if (requestMappingInfo != null) requestMappingHandlerMapping.unregisterMapping(requestMappingInfo);
            } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                throw new LoaderRuntimeException(e.getMessage(), e);
            }
        });
        // 销毁bean
        SpringContextUtils.destroy(beanName);
        try {
            // 检测HandlerMethods
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            method.setAccessible(true);
            method.invoke(requestMappingHandlerMapping, type.getConstructor().newInstance());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
        return beanName;
    }


}
