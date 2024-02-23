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
     * 注册控制器
     *
     * @param beanName 控制器的bean名称
     * @param type     控制器的类型
     */
    public static <T> void registerController(String beanName, Class<T> type) {
        registerSingleton(beanName, type);
        // 获取RequestMappingHandlerMapping对象
        RequestMappingHandlerMapping requestMappingHandlerMapping = getBean("requestMappingHandlerMapping");
        try {
            // 获取detectHandlerMethods方法
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            // 设置detectHandlerMethods方法可访问
            method.setAccessible(true);
            // 调用detectHandlerMethods方法，将控制器对象作为参数传入
            method.invoke(requestMappingHandlerMapping, beanName);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // 抛出异常
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 注销控制器
     *
     * @param beanName 控制器的bean名称
     */
    public static void unregisterController(String beanName) {
        // 获取控制器对象
        Object obj = getBean(beanName);
        // 获取控制器的类型
        Class<?> type = obj.getClass();
        // 获取RequestMappingHandlerMapping对象
        RequestMappingHandlerMapping requestMappingHandlerMapping = getBean("requestMappingHandlerMapping");
        // 遍历控制器的方法
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
        // 销毁控制器的bean
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

    @SuppressWarnings("unchecked")
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
