package cn.wubo.loader.util.aspect;

import java.lang.reflect.Method;

/**
 * 切面接口，可以继承这个接口实现自己的切面类
 */
public interface IAspect {
    /**
     * 目标方法执行前的操作
     *
     * @param target 目标对象
     * @param method 目标方法
     * @param args   参数
     */
    void before(Object target, Method method, Object[] args);

    /**
     * 目标方法执行后的操作
     *
     * @param target 目标对象
     * @param method 目标方法
     * @param args   参数
     * @param result 目标方法执行返回值
     */
    void after(Object target, Method method, Object[] args, Object result);

    /**
     * 目标方法抛出异常时的操作
     *
     * @param target 目标对象
     * @param method 目标方法
     * @param args   参数
     * @param cause  异常
     */
    void afterThrow(Object target, Method method, Object[] args, Throwable cause);
}
