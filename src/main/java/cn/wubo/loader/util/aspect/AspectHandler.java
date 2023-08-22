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

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        Object result;
        try {
            aspect.before(target, method, args);
            result = method.invoke(target, args);
            aspect.after(target, method, args, result);
        } catch (Throwable cause) {
            aspect.afterThrow(target, method, args, cause);
            throw cause;
        }
        return result;
    }
}
