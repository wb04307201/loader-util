package cn.wubo.dynamic.loader.utility.bean;

import cn.wubo.dynamic.loader.utility.exception.BeanRegistrationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * 静态门面，封装 Spring 容器中动态注册/注销 Bean 与 Controller 路由的操作。
 *
 * <p>所有 controller 操作都依赖 Spring 容器中存在 {@link DynamicRequestMappingHandlerMapping} bean。
 * 启用 {@code DynamicBeanAutoConfiguration} 后该 bean 会自动注册。
 */
public final class DynamicBean {

    private DynamicBean() {}

    /** 注册单例 Bean 定义。bean 还未实例化；首次 getBean 时实例化。 */
    public static void registerSingleton(DefaultListableBeanFactory dbf, String beanName, Class<?> type) {
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(type);
        def.setScope(BeanDefinition.SCOPE_SINGLETON);
        def.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        def.setPrimary(true);
        dbf.registerBeanDefinition(beanName, def);
    }

    public static void unregisterSingleton(DefaultListableBeanFactory dbf, String beanName) {
        if (dbf.containsBean(beanName)) {
            dbf.removeBeanDefinition(beanName);
            dbf.destroySingleton(beanName);
        }
    }

    /**
     * 注册一个 controller：先把 bean 定义注册到容器，再触发 mapping 探测其路由。
     * 如果 bean 已存在，会覆盖其 bean 定义。
     */
    public static void registerController(DefaultListableBeanFactory dbf, String beanName, Class<?> type) {
        if (dbf.containsBean(beanName)) {
            dbf.removeBeanDefinition(beanName);
            dbf.destroySingleton(beanName);
        }
        registerSingleton(dbf, beanName, type);
        Object handler = dbf.getBean(beanName);
        getMapping(dbf).registerHandler(handler);
    }

    /** 注销 controller 的路由映射（bean 定义保留）。 */
    public static void unregisterController(DefaultListableBeanFactory dbf, String beanName) {
        Object handler = dbf.getBean(beanName);
        getMapping(dbf).unregisterHandler(handler);
    }

    /** 重新注册并刷新 controller（重置类型 + 重新探测映射）。 */
    public static void refreshController(DefaultListableBeanFactory dbf, String beanName, Class<?> type) {
        registerController(dbf, beanName, type);
    }

    private static DynamicRequestMappingHandlerMapping getMapping(DefaultListableBeanFactory dbf) {
        try {
            return dbf.getBean(DynamicRequestMappingHandlerMapping.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "DynamicRequestMappingHandlerMapping bean not found. " +
                "Did you enable DynamicBeanAutoConfiguration?", e);
        }
    }
}