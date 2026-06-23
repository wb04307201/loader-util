package cn.wubo.dynamic.loader.utility.aspect;

import cn.wubo.dynamic.loader.utility.exception.ProxyCreationException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * 基于 ByteBuddy 的方法代理工厂。
 */
public final class DynamicProxy {

    private DynamicProxy() {}

    /** 通过实例提取父类。target 必须有可访问的无参构造。 */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(T target, IAdvice advice) {
        return (T) proxy(target.getClass(), advice, target.getClass().getClassLoader());
    }

    /** 显式指定父类。 */
    public static <T> T proxy(Class<T> type, IAdvice advice) {
        return proxy(type, advice, type.getClassLoader());
    }

    /** 显式指定父类 + ClassLoader。 */
    public static <T> T proxy(Class<T> type, IAdvice advice, ClassLoader loader) {
        try {
            Class<? extends T> proxyClass = new ByteBuddy()
                .subclass(type)
                .method(ElementMatchers.any())
                .intercept(MethodDelegation.to(new AdviceInterceptor(advice)))
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
            Constructor<? extends T> ctor = proxyClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new ProxyCreationException(
                "Target class " + type.getName() + " must have a no-arg constructor for proxying", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new ProxyCreationException(
                "Failed to instantiate proxy for " + type.getName(), e);
        }
    }
}
