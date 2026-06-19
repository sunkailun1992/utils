package com.kellen.utils.exception;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务校验或流程失败，由 {@code ApiExceptionHandler} 统一转换为失败响应。</p>
 *
 * @author 孙凯伦
 */
public class BusinessException extends RuntimeException{

    /**
     * 构造无消息的业务异常。
     */
    public BusinessException() {
        super();
    }

    /**
     * 构造带消息与根因的业务异常。
     *
     * @param message 业务提示信息
     * @param cause   根因异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造带消息的业务异常。
     *
     * @param message 业务提示信息
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 构造带根因的业务异常。
     *
     * @param cause 根因异常
     */
    public BusinessException(Throwable cause) {
        super(cause);
    }

}
