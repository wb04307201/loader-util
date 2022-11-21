package cn.wubo.loader.util.class_loader;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * @description: 内存文件管理器
 * @author: wubo
 * @date: 2022-11-21
 */
public class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {

	/**
	 * class内存对象
	 */
	private JavaMemClass javaMemClass;

	protected MemFileManager(JavaFileManager fileManager) {
		super(fileManager);
	}
	
	public JavaMemClass getJavaMemClass() {
		return javaMemClass;
	}
	
	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
		javaMemClass = new JavaMemClass(className, kind);
		return javaMemClass;
	}
}
