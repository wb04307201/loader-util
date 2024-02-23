package cn.wubo.loader.util;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;

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
     * @param type Bean的类型
     */
    public static <T> void registerSingleton(Class<T> type) {
        // 创建Bean实例
        T obj = listableBeanFactory.createBean(type);
        // 获取Bean名称
        String beanName = beanName(type.getName());
        // 自动装配Bean属性
        listableBeanFactory.autowireBean(obj);
        // 注册单例Bean
        listableBeanFactory.registerSingleton(beanName, obj);
    }


    /**
     * 销毁指定类名的单例对象。
     *
     * @param className 要销毁的类名
     */
    public static void destroy(String className) {
        String beanName = beanName(className);
        listableBeanFactory.destroySingleton(beanName);
    }


    /**
     * 生成bean的名称
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
