package cn.wubo.dynamic.loader.utility.bean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApp {

    @RestController("helloController")
    static class HelloController {
        @GetMapping("/api/hello")
        public String hello() { return "hello"; }
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }
}