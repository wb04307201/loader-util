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
        // 创建一个StopWatch对象，用于计时
        sw = new StopWatch(target.getClass().getName() + " " + method.getName());
        // 输出日志信息，记录StopWatch对象的ID
        log.info("SimpleAspect before " + sw.getId());
        // 启动StopWatch对象
        sw.start();
    }


    @Override
    public void after(Object target, Method method, Object[] args, Object result) {
        // 停止计时器
        sw.stop();
        // 打印日志，输出计时器ID
        log.info("SimpleAspect after " + sw.getId());
        // 打印日志，输出计时器的简要摘要
        log.info(sw.shortSummary());
    }


    @Override
    public void afterThrow(Object target, Method method, Object[] args, Throwable cause) {
        sw.stop();
        log.info("SimpleAspect afterThrow " + sw.getId() + " " + cause.getMessage());
        log.info(sw.shortSummary());
    }

}
