package cn.wubo.loader.util.controller_loader;

import cn.wubo.loader.util.SpringContextUtils;
import cn.wubo.loader.util.class_loader.DynamicClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicController {

    private DynamicClass dynamicClass;

    public DynamicController(DynamicClass dynamicClass) {
        this.dynamicClass = dynamicClass;
    }

    public static DynamicController init(DynamicClass dynamicClass) {
        log.debug("初始化controller fullClassName:{}", dynamicClass.getFullClassName());
        return new DynamicController(dynamicClass);
    }

    /**
     * 加载动态类并注册到Spring容器中。
     * 此方法首先尝试获取动态类的bean名称，然后检查该bean是否已在Spring容器中注册。
     * 如果已注册，则注销该bean；未注册则将动态类加载为Class对象，并将其注册为Spring容器中的控制器。
     *
     * @return 注册的bean名称
     */
    public String load() {
        // 获取动态类的bean名称
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        // 检查Spring容器中是否已存在该bean，若存在则注销
        if (Boolean.TRUE.equals(SpringContextUtils.containsBean(beanName))) SpringContextUtils.unregisterController(beanName);
        // 加载动态类并获取其Class对象
        Class<?> type = dynamicClass.compiler().load();
        // 将动态类的Class对象注册为Spring控制器
        SpringContextUtils.registerController(beanName, type);
        // 返回bean名称
        return beanName;
    }
}
