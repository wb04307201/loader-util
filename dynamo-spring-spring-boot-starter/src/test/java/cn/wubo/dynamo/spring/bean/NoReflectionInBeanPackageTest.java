package cn.wubo.dynamo.spring.bean;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守护测试：保证 bean 包不出现 {@code setAccessible(true)}——这是 2.0 拒绝反射私有方法的契约。
 * 如果以后有人回退到反射，这个测试会失败。
 */
class NoReflectionInBeanPackageTest {

    @Test
    void noSetAccessibleInBeanPackage() throws IOException {
        Path beanDir = Paths.get("src/main/java/cn/wubo/dynamo/spring/bean");
        try (Stream<Path> files = Files.walk(beanDir)) {
            files.filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> {
                     try {
                         String content = Files.readString(p, StandardCharsets.UTF_8);
                         assertThat(content)
                             .as("File %s must not contain setAccessible", p)
                             .doesNotContain("setAccessible");
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }
    }
}
