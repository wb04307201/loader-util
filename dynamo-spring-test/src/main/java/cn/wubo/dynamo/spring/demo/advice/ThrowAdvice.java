package cn.wubo.dynamo.spring.demo.advice;

import cn.wubo.dynamo.spring.aspect.IAdvice;

import java.lang.reflect.Method;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用于演示 {@code afterThrow} 增强点。选 {@code adviceType=throw} 后，
 * 在 UI 源码框里写一个自己抛异常的方法（例如 {@code public String boom() { throw ... }}），
 * 调用时：
 *
 * <pre>
 *   before    → 记录一条日志
 *   superCall → 用户方法抛出
 *   afterThrow → 记录一条日志（异常被 advice 重新抛出，ByteBuddy 包装为 InvocationTargetException）
 * </pre>
 *
 * <p>实现上 ThrowAdvice 自己不抛异常——只被动记录所有三个阶段的调用。
 * 之所以不在 {@code before} 里主动抛，是因为当前 {@code AdviceInterceptor} 把
 * {@code before()} 放在 try-catch 之外，{@code before} 抛出的异常不会被
 * {@code afterThrow} 接住，那是核心库的实现细节，本演示不去改。
 */
public class ThrowAdvice implements IAdvice {

    private final Deque<Map<String, Object>> log;

    public ThrowAdvice(Deque<Map<String, Object>> log) {
        this.log = log;
    }

    @Override
    public void before(Object target, Method method, Object[] args) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "before");
        entry.put("method", method.getName());
        entry.put("note", "ThrowAdvice 仅记录；让源方法自己抛异常即可看到 afterThrow");
        log.addLast(entry);
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "after");
        entry.put("method", method.getName());
        entry.put("note", "ThrowAdvice 不应到达 after（异常已抛）");
        log.addLast(entry);
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "afterThrow");
        entry.put("method", method.getName());
        entry.put("caught", cause.getClass().getName() + ": " + cause.getMessage());
        log.addLast(entry);
        // 不吞异常，让 ByteBuddy 重新抛出
    }
}
