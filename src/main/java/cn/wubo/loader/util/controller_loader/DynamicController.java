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
     * 加载动态类并注册到Spring容器中
     *
     * @return 注册的bean名称
     */
    public String load() {
        // 获取动态类的bean名称
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        // 如果Spring容器中已存在该bean，则注销该bean
        if (Boolean.TRUE.equals(SpringContextUtils.containsBean(beanName))) SpringContextUtils.unregisterController(beanName);
        // 加载动态类并获取其Class对象
        Class<?> type = dynamicClass.compiler().load();
        // 将该Class对象注册为Spring容器中的控制器
        SpringContextUtils.registerController(beanName, type);
        // 返回注册的bean名称
        return beanName;
    }


}
