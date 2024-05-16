package cn.wubo.loader.util.bean_loader;

import cn.wubo.loader.util.SpringContextUtils;
import cn.wubo.loader.util.class_loader.DynamicClass;
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
     * 加载动态类并注册到Spring容器中。
     * 此方法首先尝试获取动态类的bean名称，然后检查该bean是否已在Spring容器中存在。
     * 如果已存在，则销毁该bean。接着，加载动态类的Class对象，并将其作为单例bean注册到Spring容器中。
     *
     * @return 注册的bean名称
     */
    public String load() {
        // 获取动态类的bean名称
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        // 如果Spring容器中已存在该bean，则销毁该bean
        if (Boolean.TRUE.equals(SpringContextUtils.containsBean(beanName))) SpringContextUtils.destroy(beanName);
        // 加载动态类并获取其Class对象
        Class<?> type = dynamicClass.compiler().load();
        // 将该Class对象注册为Spring容器中的单例bean
        SpringContextUtils.registerSingleton(beanName, type);
        // 返回注册的bean名称
        return beanName;
    }
}
