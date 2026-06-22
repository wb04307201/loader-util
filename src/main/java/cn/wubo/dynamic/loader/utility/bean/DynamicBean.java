package cn.wubo.dynamic.loader.utility.bean;

import cn.wubo.dynamic.loader.utility.exception.BeanRegistrationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DynamicBean {

    /**
     * 取消注册单例bean
     *
     * @param dbf      Bean工厂实例，用于管理bean的注册和销毁
     * @param beanName 要取消注册的bean名称
     */
    public static void unregisterSingleton(DefaultListableBeanFactory dbf, String beanName) {
        // 检查bean是否存在，如果存在则移除bean定义并销毁单例实例
        if (dbf.containsBean(beanName)) {
            dbf.removeBeanDefinition(beanName);
            dbf.destroySingleton(beanName);
        }
    }

    /**
     * 向Spring容器中注册一个单例Bean定义
     *
     * @param dbf      Spring的Bean工厂，用于注册Bean定义
     * @param beanName 要注册的Bean的名称
     * @param type     要注册的Bean的类型
     */
    public static void registerSingleton(DefaultListableBeanFactory dbf, String beanName, Class<?> type) {
        // 创建Bean定义并设置相关属性
        GenericBeanDefinition patchBeanDefinition = new GenericBeanDefinition();
        patchBeanDefinition.setBeanClass(type);
        patchBeanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        patchBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        patchBeanDefinition.setPrimary(true);

        // 向Bean工厂注册Bean定义
        dbf.registerBeanDefinition(beanName, patchBeanDefinition);
    }

    /**
     * 从Spring MVC的RequestMappingHandlerMapping中注销指定控制器bean的请求映射
     *
     * @param dbf      Bean工厂实例，用于获取目标bean和RequestMappingHandlerMapping
     * @param beanName 要注销的控制器bean名称
     */
    public static void unregisterController(DefaultListableBeanFactory dbf, String beanName) {
        // 获取目标控制器对象
        Object targetObj = dbf.getBean(beanName);
        // 获取Spring MVC的请求映射处理器映射器
        RequestMappingHandlerMapping requestMappingHandlerMapping = (RequestMappingHandlerMapping) dbf.getBean("requestMappingHandlerMapping");

        // 遍历目标对象的所有方法，查找并注销对应的请求映射
        ReflectionUtils.doWithMethods(targetObj.getClass(), method -> {
            Method mostSpecificMethod = ClassUtils.getMostSpecificMethod(method, targetObj.getClass());
            try {
                // 通过反射获取RequestMappingHandlerMapping的getMappingForMethod方法
                Method declaredMethod = requestMappingHandlerMapping.getClass().getDeclaredMethod("getMappingForMethod", Method.class, Class.class);
                declaredMethod.setAccessible(true);
                // 调用getMappingForMethod方法获取请求映射信息
                RequestMappingInfo requestMappingInfo = (RequestMappingInfo) declaredMethod.invoke(requestMappingHandlerMapping, mostSpecificMethod, targetObj.getClass());
                // 如果存在请求映射信息，则将其从映射器中注销
                if (requestMappingInfo != null) requestMappingHandlerMapping.unregisterMapping(requestMappingInfo);
            } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                throw new BeanRegistrationException(e.getMessage(), e);
            }
        });
    }

    /**
     * 注销控制器Bean并重新检测处理器方法
     *
     * @param dbf      Bean工厂实例，用于获取和注册Bean
     * @param beanName 要注销的Bean名称
     * @param type     Bean的类型Class对象
     */
    public static void unregisterController(DefaultListableBeanFactory dbf, String beanName, Class<?> type) {
        // 重新注册单例Bean
        registerSingleton(dbf, beanName, type);

        // 获取RequestMappingHandlerMapping实例，用于处理请求映射
        RequestMappingHandlerMapping requestMappingHandlerMapping = (RequestMappingHandlerMapping) dbf.getBean("requestMappingHandlerMapping");
        try {
            // 通过反射获取父类的detectHandlerMethods方法，用于重新检测处理器方法
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            method.setAccessible(true);
            method.invoke(requestMappingHandlerMapping, beanName);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new BeanRegistrationException(e.getMessage(), e);
        }
    }

}
