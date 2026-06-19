package com.kellen.utils.exception;

/**
 * 参数为空异常
 * @author 孙凯伦
 * 
 */
public class ParameterNullException extends RuntimeException {

    /**
     * 构造无消息的异常。
     */
    public ParameterNullException() {
        super();
    }


    /**
     * 构造带消息与根因的异常。
     *
     * @param message 异常提示信息
     * @param cause   根因异常
     */
    public ParameterNullException(String message, Throwable cause) {
        super(message, cause);
    }


    /**
     * 构造带消息的异常。
     *
     * @param message 异常提示信息
     */
    public ParameterNullException(String message) {
        super(message);
    }


    /**
     * 构造带根因的异常。
     *
     * @param cause 根因异常
     */
    public ParameterNullException(Throwable cause) {
        super(cause);
    }
}
