package cn.wubo.loader.util;

import cn.wubo.loader.util.exception.LoaderRuntimeException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

@Component(value = "loaderUtilSpringContextUtils")
public class SpringContextUtils implements BeanFactoryAware {

    private static DefaultListableBeanFactory listableBeanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        listableBeanFactory = (DefaultListableBeanFactory) beanFactory;
    }

    /**
     * 注册单例Bean
     *
     * @param type Bean的类型
     */
    public static <T> void registerSingleton(Class<T> type) {
        registerSingleton(beanName(type.getName()), type);
    }

    /**
     * 注册单例Bean
     *
     * @param beanName Bean的名称
     * @param type     Bean的类型
     */
    public static <T> void registerSingleton(String beanName, Class<T> type) {
        // 创建Bean实例
        T obj = listableBeanFactory.createBean(type);
        // 自动装配Bean依赖
        listableBeanFactory.autowireBean(obj);
        // 注册单例Bean
        listableBeanFactory.registerSingleton(beanName, obj);
    }

    /**
     * 注册控制器类并触发Spring MVC的映射处理方法。
     *
     * 本方法主要用于在运行时动态注册控制器类，使得这些类能够被Spring MVC框架识别并处理相应的HTTP请求。
     * 通过调用registerSingleton方法将控制器类注册为Spring Bean，确保Spring容器能够管理这个类的实例。
     * 随后，通过获取RequestMappingHandlerMapping实例并调用其detectHandlerMethods方法，来触发Spring MVC对新注册控制器的映射处理。
     * 这一过程对于动态加载控制器类，例如在插件开发或者热部署场景中，是非常关键的。
     *
     * @param beanName 控制器类在Spring容器中的Bean名称。
     * @param type 控制器类的类型。
     * @throws LoaderRuntimeException 如果在尝试检测处理器方法过程中发生异常，则抛出此运行时异常。
     * @param <T> 控制器类的类型参数。
     */
    public static <T> void registerController(String beanName, Class<T> type) {
        // 注册控制器类为Spring Bean。
        registerSingleton(beanName, type);

        // 获取RequestMappingHandlerMapping实例，用于处理请求映射。
        RequestMappingHandlerMapping requestMappingHandlerMapping = getBean("requestMappingHandlerMapping");

        try {
            // 通过反射获取父类的父类（即AbstractHandlerMethodMapping）中的detectHandlerMethods方法。
            // 这是因为RequestMappingHandlerMapping自身并没有提供公开的detectHandlerMethods方法。
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            // 设置方法可访问，以绕过Java的访问控制。
            method.setAccessible(true);
            // 调用detectHandlerMethods方法，传入控制器Bean的名称，以触发映射处理。
            method.invoke(requestMappingHandlerMapping, beanName);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 如果在处理过程中发生异常，则抛出自定义的运行时异常。
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 取消注册一个控制器（bean）。
     * 该方法通过获取特定bean实例，分析并移除其上的@RequestMapping映射，从而实现控制器的注销功能。
     * 主要用于在运行时动态调整应用程序的路由配置。
     *
     * @param beanName 需要注销的控制器bean的名称。
     */
    public static void unregisterController(String beanName) {
        // 根据bean名称获取bean实例。
        Object obj = getBean(beanName);
        // 获取bean实例的类类型。
        Class<?> type = obj.getClass();

        // 获取RequestMappingHandlerMapping的实例，用于处理@RequestMapping的映射注销。
        RequestMappingHandlerMapping requestMappingHandlerMapping = getBean("requestMappingHandlerMapping");

        // 遍历类型上的所有方法，寻找并处理@RequestMapping注解的方法。
        ReflectionUtils.doWithMethods(type, method -> {
            // 获取最具体的方法，用于处理重载方法的情况。
            Method mostSpecificMethod = ClassUtils.getMostSpecificMethod(method, type);
            try {
                // 获取RequestMappingHandlerMapping中用于获取方法映射的方法。
                Method declaredMethod = requestMappingHandlerMapping.getClass().getDeclaredMethod("getMappingForMethod", Method.class, Class.class);
                // 设置该方法可访问，因为它是protected的。
                declaredMethod.setAccessible(true);
                // 调用getMappingForMethod方法，获取对应方法的RequestMappingInfo对象。
                RequestMappingInfo requestMappingInfo = (RequestMappingInfo) declaredMethod.invoke(requestMappingHandlerMapping, mostSpecificMethod, type);
                // 如果RequestMappingInfo不为空，则从映射中注销该方法。
                if (requestMappingInfo != null) requestMappingHandlerMapping.unregisterMapping(requestMappingInfo);
            } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                // 抛出运行时异常，封装原始异常信息。
                throw new LoaderRuntimeException(e.getMessage(), e);
            }
        });
        // 销毁单例bean，确保它在容器中被重新创建而不是重新使用。
        listableBeanFactory.destroySingleton(beanName);
    }

    /**
     * 判断是否包含指定类型的bean
     *
     * @param type 要判断的bean类型
     * @return 如果包含指定类型的bean则返回true，否则返回false
     */
    public static <T> Boolean containsBean(Class<T> type) {
        return listableBeanFactory.containsBean(beanName(type.getName()));
    }

    /**
     * 判断给定的bean名称是否存在于listableBeanFactory中
     *
     * @param beanName 要判断的bean名称
     * @return 如果bean名称存在于listableBeanFactory中，则返回true；否则返回false
     */
    public static Boolean containsBean(String beanName) {
        return listableBeanFactory.containsBean(beanName);
    }

    /**
     * 销毁指定名称的单例对象。
     *
     * @param beanName 要销毁的单例对象的名称
     */
    public static void destroy(String beanName) {
        listableBeanFactory.destroySingleton(beanName);
    }

    /**
     * 销毁指定类型的单例对象。
     *
     * @param type 指定的类型
     */
    public static <T> void destroy(Class<T> type) {
        listableBeanFactory.destroySingleton(beanName(type.getName()));
    }

    /**
     * 生成bean的名称
     *
     * @param className 类名
     * @return bean的名称
     */
    public static String beanName(String className) {
        String[] path = className.split("\\.");
        String beanName = path[path.length - 1];
        return Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);
    }

    public static <T> T getBean(String name) {
        return (T) listableBeanFactory.getBean(name);
    }

    public static <T> T getBean(Class<T> type) {
        return listableBeanFactory.getBean(type);
    }

    public static <T> Map<String, T> getBeans(Class<T> type) {
        return listableBeanFactory.getBeansOfType(type);
    }
}
