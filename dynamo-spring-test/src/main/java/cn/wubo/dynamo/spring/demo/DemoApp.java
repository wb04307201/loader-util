package cn.wubo.dynamo.spring.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 演示应用入口。
 *
 * <p>通过 {@code mvn spring-boot:run -pl dynamic-loader-utility-test -am} 启动，
 * 浏览器访问 {@code http://localhost:8080/} 即可看到 4 个 tab 的 playground。
 *
 * <p>该 main class 故意放在 {@code cn.wubo.dynamo.spring.demo} 包下，
 * 避免被 starter 内部的组件扫描影响。
 */
@SpringBootApplication
public class DemoApp {

    public static void main(String[] args) {
        SpringApplication.run(DemoApp.class, args);
    }
}
