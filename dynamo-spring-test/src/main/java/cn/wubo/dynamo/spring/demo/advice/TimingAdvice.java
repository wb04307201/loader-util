package cn.wubo.dynamo.spring.demo.advice;

import cn.wubo.dynamo.spring.aspect.IAdvice;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用 {@link StopWatch} 计时并把结果写入日志队列。
 *
 * <p>ThreadLocal 模式跟 starter 自带的 {@code SimpleAdvice} 一致——已知不支持
 * 同线程 re-entrant 调用。demo 用法下没问题。
 */
public class TimingAdvice implements IAdvice {

    private final ThreadLocal<StopWatch> timer = new ThreadLocal<>();
    private final Deque<Map<String, Object>> log;

    public TimingAdvice(Deque<Map<String, Object>> log) {
        this.log = log;
    }

    @Override
    public void before(Object target, Method method, Object[] args) {
        StopWatch sw = new StopWatch(target.getClass().getName() + "#" + method.getName());
        sw.start();
        timer.set(sw);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "before");
        entry.put("method", method.getName());
        log.addLast(entry);
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "after");
        entry.put("method", method.getName());
        entry.put("elapsedMs", sw.getTotalTimeMillis());
        if (result != null) {
            entry.put("result", String.valueOf(result));
        }
        log.addLast(entry);
        timer.remove();
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("phase", "afterThrow");
        entry.put("method", method.getName());
        entry.put("elapsedMs", sw.getTotalTimeMillis());
        entry.put("error", cause.getClass().getName() + ": " + cause.getMessage());
        log.addLast(entry);
        timer.remove();
    }
}
