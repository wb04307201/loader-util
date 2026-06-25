package cn.wubo.dynamo.spring.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 编译选项的流式构建器。
 */
public class CompilerOptions {

    private final List<String> options = new ArrayList<>();

    public static CompilerOptions create() {
        return new CompilerOptions();
    }

    public CompilerOptions sourceVersion(String version) {
        options.add("-source");
        options.add(version);
        return this;
    }

    public CompilerOptions targetVersion(String version) {
        options.add("-target");
        options.add(version);
        return this;
    }

    public CompilerOptions enableDebug() {
        options.add("-g");
        return this;
    }

    public CompilerOptions disableDebug() {
        options.add("-g:none");
        return this;
    }

    public CompilerOptions addOption(String option) {
        options.add(option);
        return this;
    }

    public CompilerOptions addOptions(String... opts) {
        for (String opt : opts) {
            options.add(opt);
        }
        return this;
    }

    /** 添加 classpath 路径（-cp）。 */
    public CompilerOptions classpath(String... paths) {
        if (paths.length == 0) return this;
        String existing = findExisting("-cp");
        String combined = String.join(File.pathSeparator, paths);
        if (existing == null) {
            options.add("-cp");
            options.add(combined);
        } else {
            int idx = options.indexOf(existing);
            options.set(idx, existing + File.pathSeparator + combined);
        }
        return this;
    }

    /** 添加 sourcepath。 */
    public CompilerOptions sourcepath(String... paths) {
        options.add("-sourcepath");
        options.add(String.join(File.pathSeparator, paths));
        return this;
    }

    /** 启用预览特性（--release + --enable-preview 由调用方组合）。本方法仅添加 --enable-preview 标志。 */
    public CompilerOptions enablePreview() {
        options.add("--enable-preview");
        return this;
    }

    public List<String> build() {
        return new ArrayList<>(options);
    }

    private String findExisting(String flag) {
        int idx = options.indexOf(flag);
        return idx >= 0 ? options.get(idx + 1) : null;
    }
}
