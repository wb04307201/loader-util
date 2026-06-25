package cn.wubo.dynamo.spring.exception;

/**
 * 动态代理创建失败时抛出的运行时异常。
 */
public class ProxyCreationException extends RuntimeException {

    public ProxyCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}