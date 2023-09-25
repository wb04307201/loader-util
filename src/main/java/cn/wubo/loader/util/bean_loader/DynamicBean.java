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

    public static DynamicBean init(DynamicClass dynamicClass) {
        log.debug("初始化bean fullClassName:{}", dynamicClass.getFullClassName());
        return new DynamicBean(dynamicClass);
    }

    public String load() {
        String beanName = SpringContextUtils.beanName(dynamicClass.getFullClassName());
        //销毁Bean
        SpringContextUtils.destroy(beanName);
        //每次都是new新的ClassLoader对象
        Class<?> type = dynamicClass.compiler().load();
        SpringContextUtils.registerSingleton(type);
        return beanName;
    }
}
