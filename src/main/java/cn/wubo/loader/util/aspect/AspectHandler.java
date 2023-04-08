package cn.wubo.loader.util.aspect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class AspectHandler implements InvocationHandler {

    IAspect aspect;

    public AspectHandler(IAspect aspect) {
        this.aspect = aspect;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result;
        try {
            aspect.before(proxy, method, args);
            result = method.invoke(proxy, args);
            aspect.after(proxy, method, args, result);
        } catch (Throwable cause) {
            aspect.afterThrow(proxy, method, args, cause);
            throw cause;
        }
        return result;
    }
}
