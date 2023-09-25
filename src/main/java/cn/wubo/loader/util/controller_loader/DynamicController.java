package cn.wubo.loader.util.controller_loader;

import cn.wubo.loader.util.class_loader.DynamicClass;
import cn.wubo.loader.util.SpringContextUtils;
import cn.wubo.loader.util.exception.LoaderRuntimeException;
import lombok.extern.slf4j.Slf4j;
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

    public String load() {
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        //销毁Bean
        SpringContextUtils.destroy(beanName);
        //销毁Bean
        SpringContextUtils.destroy(beanName);
        //每次都是new新的ClassLoader对象
        Class<?> type = dynamicClass.compiler().load();
        RequestMappingHandlerMapping requestMappingHandlerMapping = SpringContextUtils.getBean("requestMappingHandlerMapping");
        //注册Controller
        try {
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            method.setAccessible(true);
            method.invoke(requestMappingHandlerMapping, type);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
        return beanName;
    }
}
