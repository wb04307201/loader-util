package cn.wubo.loader.util.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;

/**
 * 切面类，简单的输出和记录方法执行时间
 */
@Slf4j
public class SimpleAspect implements IAspect {

    private StopWatch sw;

    @Override
    public void before(Object target, Method method, Object[] args) {
        sw = new StopWatch(target.getClass().getName() + " " + method.getName());
        log.info("SimpleAspect before " + sw.getId());
        sw.start();
    }

    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        sw.stop();
        log.info("SimpleAspect after " + sw.getId());
        log.info(sw.shortSummary());
    }

    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        sw.stop();
        log.info("SimpleAspect afterThrow " + sw.getId() + " " + cause.getMessage());
        log.info(sw.shortSummary());
    }
}
