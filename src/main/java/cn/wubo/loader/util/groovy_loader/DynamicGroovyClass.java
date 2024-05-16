package cn.wubo.loader.util.groovy_loader;


import cn.wubo.loader.util.exception.LoaderRuntimeException;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DynamicGroovyClass {

    private String javaSourceCode;

    public DynamicGroovyClass(String javaSourceCode) {
        this.javaSourceCode = javaSourceCode;
    }

    /**
     * 初始化 DynamicGroovy 工具类。这个方法的作用是通过提供的Java源代码字符串，
     * 来创建并初始化一个DynamicGroovy对象。
     *
     * @param javaSourceCode Java 源代码字符串。这个参数是待初始化的DynamicGroovy对象
     *                       所需要的Java源代码内容。
     * @return DynamicGroovy 对象。返回一个初始化完成的DynamicGroovy实例，
     *         该实例可以用于进一步的动态执行Java代码操作。
     */
    public static DynamicGroovyClass init(String javaSourceCode) {
        // 记录初始化过程中的Java源代码信息，以备调试之用
        log.debug("初始化 groovy javaSourceCode:{}", javaSourceCode);
        return new DynamicGroovyClass(javaSourceCode);
    }

    /**
     * 加载并解析一个Groovy类或脚本的Class对象。
     * 这个方法不接受任何参数，它内部使用GroovyClassLoader来加载指定的Groovy源代码，
     * 并返回解析后的Class对象。
     *
     * @return 解析后的Class对象。这个Class对象可以用来实例化Groovy类或执行Groovy脚本。
     * @throws LoaderRuntimeException 如果在加载或解析Groovy源代码时发生IOException，将抛出此运行时异常。
     */
    public Class<?> load() {
        // 使用GroovyClassLoader来加载Groovy源代码
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            // 解析Groovy源代码为Class对象
            return groovyClassLoader.parseClass(javaSourceCode);
        } catch (IOException e) {
            // 处理加载或解析过程中可能发生的IO异常
            throw new LoaderRuntimeException(e.getMessage(), e);
        }
    }

}
