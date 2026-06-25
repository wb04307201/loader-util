package cn.wubo.dynamo.spring.compiler;

import org.junit.jupiter.api.Test;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryFileManagerTest {

    private static StandardJavaFileManager standard() {
        return ToolProvider.getSystemJavaCompiler()
            .getStandardFileManager(null, null, null);
    }

    @Test
    void getJavaFileForOutput_classKind_returnsMemoryClassFileObject() throws IOException {
        try (StandardJavaFileManager sfm = standard()) {
            DynamoClassLoader loader = DynamoClassLoader.create();
            MemoryFileManager mgr = new MemoryFileManager(sfm, loader);

            FileObject sibling = null;
            JavaFileObject out = mgr.getJavaFileForOutput(
                StandardLocation.CLASS_OUTPUT, "com.example.Foo", JavaFileObject.Kind.CLASS, sibling);

            assertThat(out).isInstanceOf(MemoryClassFileObject.class);
        }
    }

    @Test
    void getJavaFileForOutput_sourceKind_delegatesToUnderlying() throws IOException {
        try (StandardJavaFileManager sfm = standard()) {
            DynamoClassLoader loader = DynamoClassLoader.create();
            MemoryFileManager mgr = new MemoryFileManager(sfm, loader);

            FileObject sibling = null;
            JavaFileObject out = mgr.getJavaFileForOutput(
                StandardLocation.SOURCE_OUTPUT, "Foo", JavaFileObject.Kind.SOURCE, sibling);

            // The delegated call should return a real file object backed by the standard manager
            assertThat(out).isNotNull();
            assertThat(out).isNotInstanceOf(MemoryClassFileObject.class);
        }
    }
}
