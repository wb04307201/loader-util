package cn.wubo.loader.util.class_loader;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * @description:java源码保存对象(内存：不生成class文件)
 * @author: wubo
 * @date: 2022-11-21
 */
public class JavaMemSource extends SimpleJavaFileObject{

	/**
     * java源码
	 */
	private String javaSourceCode;

	public JavaMemSource(String name, String javaSourceCode) {
		super(URI.create("string:///" + name.replace('.', '/')+ Kind.SOURCE.extension), Kind.SOURCE);
		this.javaSourceCode = javaSourceCode;
	}
	
	@Override 
	public CharSequence getCharContent(boolean ignoreEncodingErrors) { 
		return javaSourceCode;
	} 

}
