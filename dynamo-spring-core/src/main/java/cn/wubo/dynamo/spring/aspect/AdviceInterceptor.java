package cn.wubo.dynamo.spring.aspect;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * ByteBuddy {@code MethodDelegation} 的适配器。把每次方法调用桥接到 {@link IAdvice}。
 */
public class AdviceInterceptor {

    private final IAdvice advice;

    public AdviceInterceptor(IAdvice advice) {
        this.advice = advice;
    }

    @RuntimeType
    public Object intercept(@This Object self,
                            @Origin Method method,
                            @AllArguments Object[] args,
                            @SuperCall Callable<?> superCall) throws Exception {
        advice.before(self, method, args);
        try {
            Object result = superCall.call();
            advice.after(self, method, args, result);
            return result;
        } catch (Throwable t) {
            advice.afterThrow(self, method, args, t);
            throw t;
        }
    }
}
