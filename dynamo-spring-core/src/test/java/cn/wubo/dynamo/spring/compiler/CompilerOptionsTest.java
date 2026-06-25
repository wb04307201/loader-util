package cn.wubo.dynamo.spring.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompilerOptionsTest {

    @Test
    void create_returnsEmptyOptions() {
        assertThat(CompilerOptions.create().build()).isEmpty();
    }

    @Test
    void sourceVersion_addsFlag() {
        List<String> opts = CompilerOptions.create().sourceVersion("17").build();
        assertThat(opts).containsExactly("-source", "17");
    }

    @Test
    void targetVersion_addsFlag() {
        List<String> opts = CompilerOptions.create().targetVersion("17").build();
        assertThat(opts).containsExactly("-target", "17");
    }

    @Test
    void enableDebug_addsDashG() {
        assertThat(CompilerOptions.create().enableDebug().build()).containsExactly("-g");
    }

    @Test
    void disableDebug_addsDashGNone() {
        assertThat(CompilerOptions.create().disableDebug().build()).containsExactly("-g:none");
    }

    @Test
    void addOption_passesThrough() {
        assertThat(CompilerOptions.create().addOption("-Xlint:all").build())
            .containsExactly("-Xlint:all");
    }

    @Test
    void addOptions_passesAllThrough() {
        assertThat(CompilerOptions.create().addOptions("-X", "-Y").build())
            .containsExactly("-X", "-Y");
    }

    @Test
    void classpath_single_addsDashCp() {
        List<String> opts = CompilerOptions.create().classpath("a.jar", "b.jar").build();
        assertThat(opts).contains("-cp", "a.jar" + java.io.File.pathSeparator + "b.jar");
    }

    @Test
    void classpath_empty_isNoOp() {
        assertThat(CompilerOptions.create().classpath().build()).isEmpty();
    }

    @Test
    void classpath_multipleCalls_areMerged() {
        List<String> opts = CompilerOptions.create()
            .classpath("a.jar")
            .classpath("b.jar")
            .build();
        // -cp 出现一次，值用 pathSeparator 合并
        long cpCount = opts.stream().filter("-cp"::equals).count();
        assertThat(cpCount).isEqualTo(1);
        assertThat(opts.get(opts.indexOf("-cp") + 1))
            .contains("a.jar")
            .contains("b.jar");
    }

    @Test
    void sourcepath_addsFlag() {
        List<String> opts = CompilerOptions.create().sourcepath("src/main/java").build();
        assertThat(opts).containsExactly("-sourcepath", "src/main/java");
    }

    @Test
    void enablePreview_addsFlag() {
        assertThat(CompilerOptions.create().enablePreview().build())
            .containsExactly("--enable-preview");
    }

    @Test
    void builder_chainsAllFlags() {
        List<String> opts = CompilerOptions.create()
            .sourceVersion("17")
            .targetVersion("17")
            .enableDebug()
            .disableDebug()              // 后写覆盖前写
            .addOption("-Xlint:all")
            .addOptions("-X", "-Y")
            .classpath("a.jar")
            .sourcepath("src")
            .enablePreview()
            .build();
        // 2 (source) + 2 (target) + 1 (-g) + 1 (-g:none) + 1 (-Xlint:all) +
        // 2 (-X, -Y) + 2 (-cp, a.jar) + 2 (-sourcepath, src) + 1 (--enable-preview)
        assertThat(opts).hasSize(14);
        assertThat(opts).contains("-source", "17", "-target", "17",
            "-g:none", "-Xlint:all", "-X", "-Y",
            "-cp", "-sourcepath", "--enable-preview");
    }

    @Test
    void build_returnsDefensiveCopy() {
        CompilerOptions opts = CompilerOptions.create().sourceVersion("17");
        List<String> first = opts.build();
        first.clear();
        // 第二次 build 不应受第一次修改影响
        assertThat(opts.build()).containsExactly("-source", "17");
    }
}
