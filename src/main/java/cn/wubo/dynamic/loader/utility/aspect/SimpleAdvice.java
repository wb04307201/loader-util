package cn.wubo.dynamic.loader.utility.aspect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;

/**
 * 简单切面：记录目标方法的执行时间。
 *
 * <p><b>已知限制：</b>不支持同线程的 re-entrant 调用——同线程在 {@code before} 与 {@code after}
 * 之间再次进入任何用 {@code SimpleAdvice} 代理的方法时，内层会覆盖外层的计时器。
 */
public class SimpleAdvice implements IAdvice {

    private static final Logger log = LoggerFactory.getLogger(SimpleAdvice.class);

    private final ThreadLocal<StopWatch> timer = new ThreadLocal<>();

    @Override
    public void before(Object target, Method method, Object[] args) {
        StopWatch sw = new StopWatch(target.getClass().getName() + "#" + method.getName());
        sw.start();
        timer.set(sw);
        log.info("SimpleAdvice before {}", sw.getId());
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();
        log.info("SimpleAdvice after {}", sw.getId());
        log.info(sw.shortSummary());
        timer.remove();
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        StopWatch sw = timer.get();
        if (sw == null) return;
        sw.stop();
        log.info("SimpleAdvice afterThrow {} {}", sw.getId(), cause.getMessage());
        log.info(sw.shortSummary());
        timer.remove();
    }
}