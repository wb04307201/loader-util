package cn.wubo.loader.util;

import cn.wubo.loader.util.aspect.AspectHandler;
import cn.wubo.loader.util.aspect.IAspect;
import cn.wubo.loader.util.aspect.SimpleAspect;
import cn.wubo.loader.util.bean_loader.SpringContextUtil;
import cn.wubo.loader.util.class_loader.DynamicClass;
import org.springframework.cglib.proxy.Enhancer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 执行方法和代理切面工具类
 */
public class MethodUtils {

    private MethodUtils() {
    }

    /**
     * 执行类中的方法
     *
     * @param clazz      目标类
     * @param methodName 目标方法名
     * @param args       参数
     * @return 返回值，Object类型
     */
    public static Object invokeClass(Class<?> clazz, String methodName, Object... args) {
        try {
            Object o = clazz.newInstance();
            Class<?>[] parameterTypes = Arrays.stream(args).map(Object::getClass).collect(Collectors.toList()).toArray(new Class<?>[]{});
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(o, args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行对象中的方法
     *
     * @param target     目标对象
     * @param methodName 目标方法名
     * @param args       参数
     * @return 返回值，泛型
     */
    public static <T, R> R invokeClass(T target, String methodName, Object... args) {
        try {
            Class<?>[] parameterTypes = Arrays.stream(args).map(Object::getClass).collect(Collectors.toList()).toArray(new Class<?>[]{});
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (R) method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行Bean中的方法
     *
     * @param beanName   目标bean名称
     * @param methodName 目标方法名
     * @param args       参数
     * @return 返回值，泛型
     */
    public static <R> R invokeBean(String beanName, String methodName, Object... args) {
        return (R) invokeBeanReturnObject(beanName, methodName, args);
    }


    /**
     * 执行Bean中的方法
     *
     * @param beanName   目标bean名称
     * @param methodName 目标方法名
     * @param args       参数
     * @return 返回值, Object类型
     */
    public static Object invokeBeanReturnObject(String beanName, String methodName, Object... args) {
        try {
            Object obj = SpringContextUtil.getBean(beanName);
            Class<?>[] parameterTypes = Arrays.stream(args).map(Object::getClass).collect(Collectors.toList()).toArray(new Class<?>[]{});
            Method method = obj.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(obj, args);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用切面代理对象，默认使用SimpleAspect切面
     *
     * @param target 目标对象
     * @return 被代理的切面，Object类型
     */
    public static <T> T proxy(T target) {
        return proxy(target, new SimpleAspect());
    }

    /**
     * 使用切面代理对象
     *
     * @param target      目标对象
     * @param aspectClass 切面类
     * @return 被代理的切面，Object类型
     */
    public static <T, E extends IAspect> T proxy(T target, Class<E> aspectClass) {
        try {
            return proxy(target, aspectClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用切面代理对象
     *
     * @param target       目标对象
     * @param aspectTarget 切面对象
     * @return 被代理的切面
     */
    public static <T, E extends IAspect> T proxy(T target, E aspectTarget) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        enhancer.setCallback(new AspectHandler(target, aspectTarget));
        return (T) enhancer.create();
    }
}
