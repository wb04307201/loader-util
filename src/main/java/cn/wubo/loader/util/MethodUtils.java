package cn.wubo.loader.util;

import cn.wubo.loader.util.aspect.AspectHandler;
import cn.wubo.loader.util.aspect.IAspect;
import cn.wubo.loader.util.aspect.SimpleAspect;
import cn.wubo.loader.util.bean_loader.SpringContextUtil;
import org.springframework.cglib.proxy.Enhancer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 执行方法和代理切面工具类
 */
public class MethodUtils {

    /**
     * 执行类中的方法
     *
     * @param clazz      类
     * @param methodName 方法名
     * @param args       参数
     * @return 返回值
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

    public static Object invokeClass(Object target, String methodName, Object... args) {
        try {
            Class<?>[] parameterTypes = Arrays.stream(args).map(Object::getClass).collect(Collectors.toList()).toArray(new Class<?>[]{});
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行Bean中的方法
     *
     * @param beanName   bean名称
     * @param methodName 方法名
     * @param args       参数
     * @return 返回值
     */
    public static Object invokeBean(String beanName, String methodName, Object... args) {
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
     * @param clazz 目标对象
     * @return 被代理的切面
     */
    public static Object proxy(Class<?> clazz) {
        return proxy(clazz, SimpleAspect.class);
    }

    /**
     * 使用切面代理对象
     *
     * @param clazz       目标对象类
     * @param aspectClass 切面对象类
     * @return 被代理的切面
     */
    public static Object proxy(Class<?> clazz, Class<? extends IAspect> aspectClass) {
        try {
            final Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(clazz);
            enhancer.setCallback(new AspectHandler(clazz.newInstance(), aspectClass.newInstance()));
            return enhancer.create();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
