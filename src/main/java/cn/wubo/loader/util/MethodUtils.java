package cn.wubo.loader.util;

import cn.wubo.loader.util.bean_loader.SpringContextUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MethodUtils {

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
}
