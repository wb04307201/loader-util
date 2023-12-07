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

    /**
     * 初始化 DynamicGroovy 工具类
     *
     * @param javaSourceCode Java 源代码字符串
     * @return DynamicGroovy 对象
     */
    public static DynamicGroovy init(String javaSourceCode) {
        log.debug("初始化 groovy javaSourceCode:{}", javaSourceCode);
        return new DynamicGroovy(javaSourceCode);
    }


    /**
     * 加载并解析一个Groovy类或脚本的Class对象。
     *
     * @return 解析后的Class对象
     */
    public Class<?> load() {
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            return groovyClassLoader.parseClass(javaSourceCode);
        } catch (IOException e) {
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

}
