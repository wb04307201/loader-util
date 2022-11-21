package cn.wubo.loader.util.bean_loader;

import cn.wubo.loader.util.class_loader.DynamicClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DynamicBean {

    private DynamicClass dynamicClass;

    public DynamicBean(DynamicClass dynamicClass) {
        this.dynamicClass = dynamicClass;
    }

    public static DynamicBean init(DynamicClass dynamicClass) {
        return new DynamicBean(dynamicClass);
    }

    public String load() {
        //销毁Bean
        SpringContextUtil.destroy(dynamicClass.getFullClassName());
        //每次都是new新的ClassLoader对象
        Class<?> type = dynamicClass.compiler().load();
        SpringContextUtil.registerSingleton(type);
        return SpringContextUtil.beanName(dynamicClass.getFullClassName());
    }
}
