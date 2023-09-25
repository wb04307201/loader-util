package cn.wubo.loader.util.groovy_loader;


import cn.wubo.loader.util.exception.LoaderRuntimeException;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DynamicGroovy {

    private String javaSourceCode;

    public DynamicGroovy(String javaSourceCode) {
        this.javaSourceCode = javaSourceCode;
    }

    public static DynamicGroovy init(String javaSourceCode) {
        log.debug("初始化groovy javaSourceCode:{}", javaSourceCode);
        return new DynamicGroovy(javaSourceCode);
    }

    public Class<?> load() {
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            return groovyClassLoader.parseClass(javaSourceCode);
        } catch (IOException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }
}
