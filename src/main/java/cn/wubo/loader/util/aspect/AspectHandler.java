package cn.wubo.loader.util.aspect;


import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public class AspectHandler implements MethodInterceptor {

    Object target;
    IAspect aspect;

    public AspectHandler(Object target, IAspect aspect) {
        this.target = target;
        this.aspect = aspect;
    }

    /**
     * 重写intercept方法，在每次调用被拦截的方法时执行一系列的增强操作。
     *
     * @param obj       目标对象
     * @param method    被拦截的方法
     * @param args      方法参数
     * @param methodProxy 方法代理
     * @return 方法执行结果
     * @throws Throwable 可抛出方法执行过程中的异常
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        Object result;
        try {
            aspect.before(target, method, args);  // 在执行被拦截的方法之前执行before增强操作
            result = method.invoke(target, args); // 调用被拦截的方法并获取执行结果
            aspect.after(target, method, args, result);  // 在执行被拦截的方法之后执行after增强操作
        } catch (Throwable cause) {
            aspect.afterThrow(target, method, args, cause);  // 在抛出被拦截的方法时执行afterThrow增强操作
            throw cause;  // 抛出被拦截的方法时抛出的异常
        }
        return result;  // 返回被拦截的方法执行结果
    }

}
