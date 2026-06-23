package cn.wubo.dynamic.loader.utility.aspect;

import java.lang.reflect.Method;

/**
 * 切面接口。可以继承这个接口实现自己的切面类。
 *
 * <p>三个方法分别在目标方法的前、后、异常时被调用。
 */
public interface IAdvice {

    /** 目标方法执行前。 */
    void before(Object target, Method method, Object[] args);

    /** 目标方法正常返回后。 */
    void after(Object target, Method method, Object[] args, Object result);

    /** 目标方法抛异常后；之后异常会重新抛出。 */
    void afterThrow(Object target, Method method, Object[] args, Throwable cause);
}
