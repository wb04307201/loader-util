package cn.wubo.dynamic.loader.utility.demo.advice;

import cn.wubo.dynamic.loader.utility.aspect.IAdvice;

import java.lang.reflect.Method;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@link IAdvice} 三方法的调用记录到内存队列，供前端读取展示。
 *
 * <p>{@code args} / {@code result} 用 {@link Object#toString()} 简化展示；
 * 真实场景下可换成 JSON 序列化。
 */
public class LoggingAdvice implements IAdvice {

    private final Deque<Map<String, Object>> log;

    public LoggingAdvice(Deque<Map<String, Object>> log) {
        this.log = log;
    }

    @Override
    public void before(Object target, Method method, Object[] args) {
        log.addLast(entry("before", target, method, args, null, null));
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        log.addLast(entry("after", target, method, args, result, null));
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        log.addLast(entry("afterThrow", target, method, args, null, cause));
    }

    private static Map<String, Object> entry(String phase, Object target, Method method,
                                             Object[] args, Object result, Throwable cause) {
        // LinkedHashMap 保留插入顺序，输出更可读
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", phase);
        m.put("target", target.getClass().getName());
        m.put("method", method.getName());
        m.put("args", stringifyArgs(args));
        if (result != null) {
            m.put("result", String.valueOf(result));
        }
        if (cause != null) {
            m.put("error", cause.getClass().getName() + ": " + cause.getMessage());
        }
        return m;
    }

    private static Object stringifyArgs(Object[] args) {
        if (args == null) return new String[0];
        String[] out = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i] == null ? "null" : args[i].toString();
        }
        return out;
    }
}
