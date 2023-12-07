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
	
	/**
	 * 根据给定参数获取用于输出Java代码的JavaFileObject对象。
	 *
	 * @param location 代码发生的位置
	 * @param className 类名
	 * @param kind Java文件对象的类型
	 * @param sibling 与新创建的JavaFileObject具有相同父级文件对象的兄弟文件对象
	 * @return 用于输出Java代码的JavaFileObject对象
	 */
	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
	    javaMemClass = new JavaMemClass(className, kind);
	    return javaMemClass;
	}

}
