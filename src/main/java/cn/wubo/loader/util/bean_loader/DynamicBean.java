package cn.wubo.loader.util.bean_loader;

import cn.wubo.loader.util.class_loader.DynamicClass;
import cn.wubo.loader.util.SpringContextUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicBean {

    private DynamicClass dynamicClass;

    public DynamicBean(DynamicClass dynamicClass) {
        this.dynamicClass = dynamicClass;
    }

    /**
     * 初始化一个动态类生成的动态Bean对象
     *
     * @param dynamicClass 动态类对象，表示描述生成动态Bean的类信息
     * @return 初始化完成的动态Bean对象
     */
    public static DynamicBean init(DynamicClass dynamicClass) {
        log.debug("初始化bean fullClassName:{}", dynamicClass.getFullClassName());
        return new DynamicBean(dynamicClass);
    }


    /**
     * 加载动态生成的类并将其注册为单例Bean，
     * 销毁同名的Bean对象，返回新加载类的Bean名称
     * @return 新加载类的Bean名称
     */
    public String load() {
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        // 销毁Bean
        SpringContextUtils.destroy(beanName);
        // 每次都是new新的ClassLoader对象
        Class<?> type = dynamicClass.compiler().load();
        SpringContextUtils.registerSingleton(type);
        return beanName;
    }

}
