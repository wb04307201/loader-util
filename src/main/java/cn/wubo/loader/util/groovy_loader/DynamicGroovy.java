package cn.wubo.loader.util.groovy_loader;


import groovy.lang.GroovyClassLoader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DynamicGroovy {

    private String javaSourceCode;
    @Getter
    private String fullClassName;

    public DynamicGroovy(String javaSourceCode, String fullClassName) {
        this.javaSourceCode = javaSourceCode;
        this.fullClassName = fullClassName;
    }

    public static DynamicGroovy init(String javaSourceCode, String fullClassName) {
        log.debug("初始化groovy javaSourceCode:{} fullClassName:{}", javaSourceCode, fullClassName);
        return new DynamicGroovy(javaSourceCode, fullClassName);
    }

    public Class<?> load() {
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            return groovyClassLoader.parseClass(javaSourceCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
