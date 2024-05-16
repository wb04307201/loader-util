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
	
	/**
	 * 此方法用于获取源代码的注释内容。
	 * 它会返回源代码文件中的字符内容，包括注释部分。
	 *
	 * @param ignoreEncodingErrors 指定是否忽略编码错误。如果设置为true，
	 *                             在读取源代码文件时将忽略任何编码错误，
	 *                             否则遇到编码错误将抛出异常。
	 * @return 返回源代码的字符序列，包括代码中的所有注释。
	 */
	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
	    // 直接返回存储的源代码字符序列
	    return javaSourceCode;
	}
}
