package cn.wubo.loader.util.exception;

public class LoaderRuntimeException extends RuntimeException {
    public LoaderRuntimeException(String message) {
        super(message);
    }

    public LoaderRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
