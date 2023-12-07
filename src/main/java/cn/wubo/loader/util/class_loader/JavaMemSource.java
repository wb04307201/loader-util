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
	 * 获取源代码的注释。
	 *
	 * @param ignoreEncodingErrors 是否忽略编码错误。
	 * @return 源代码的字符内容。
	 */
	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
	    return javaSourceCode;
	}


}
