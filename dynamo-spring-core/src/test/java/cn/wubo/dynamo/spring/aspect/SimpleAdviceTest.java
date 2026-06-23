package cn.wubo.dynamo.spring.aspect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleAdviceTest {

    @Test
    void beforeAfter_oneCycle() throws Exception {
        SimpleAdvice advice = new SimpleAdvice();
        Object target = new Object();
        Method m = String.class.getMethod("length");
        advice.before(target, m, new Object[]{"abc"});
        advice.after(target, m, new Object[]{"abc"}, 3);
    }

    @Test
    void afterThrow_clearsTimer() throws Exception {
        SimpleAdvice advice = new SimpleAdvice();
        Object target = new Object();
        Method m = String.class.getMethod("length");
        advice.before(target, m, new Object[]{"abc"});
        advice.afterThrow(target, m, new Object[]{"abc"}, new RuntimeException("x"));
        advice.before(target, m, new Object[]{"def"});
        advice.after(target, m, new Object[]{"def"}, 3);
    }

    @Test
    void after_withoutBefore_isNoOp() throws Exception {
        SimpleAdvice advice = new SimpleAdvice();
        Object target = new Object();
        Method m = String.class.getMethod("length");
        advice.after(target, m, new Object[]{}, 0);
    }
}
