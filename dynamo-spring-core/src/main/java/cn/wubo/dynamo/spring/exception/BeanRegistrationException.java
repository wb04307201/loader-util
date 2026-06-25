package cn.wubo.dynamo.spring.exception;

/**
 * Spring Bean 动态注册/注销过程中抛出的运行时异常。
 */
public class BeanRegistrationException extends RuntimeException {

    public BeanRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}