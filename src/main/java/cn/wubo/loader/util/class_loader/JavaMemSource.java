package cn.wubo.loader.util.class_loader;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * JavaMemSource类继承自SimpleJavaFileObject，用于在内存中表示Java源代码。
 * 该类的主要作用是提供一种方式来存储和访问Java源代码字符串，而不需要将源代码写入到实际的文件中。
 */
public class JavaMemSource extends SimpleJavaFileObject{

	/**
	 * 存储Java源代码的字符串。
	 */
	private String javaSourceCode;

	/**
	 * 构造函数初始化JavaMemSource对象。
	 *
	 * @param name Java源文件的全限定名，使用'.'作为包名的分隔符。
	 * @param javaSourceCode Java源代码的字符串表示。
	 */
	public JavaMemSource(String name, String javaSourceCode) {
		super(URI.create("string:///" + name.replace('.', '/')+ Kind.SOURCE.extension), Kind.SOURCE);
		this.javaSourceCode = javaSourceCode;
	}

	/**
	 * 获取Java源代码的字符序列。
	 *
	 * @param ignoreEncodingErrors 是否忽略编码错误。该参数在此实现中未使用，因为源代码以字符串形式存储。
	 * @return 返回Java源代码的字符序列。
	 */
	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
	    return javaSourceCode;
	}
}

