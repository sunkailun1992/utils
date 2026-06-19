package com.kellen.utils.exception;

/**
 * 发送短信异常
 * @author 孙凯伦
 * 
 */
public class SmsException extends RuntimeException {
    /**
     * 构造无消息的异常。
     */
    public SmsException() {
        super();
    }


    /**
     * 构造带消息与根因的异常。
     *
     * @param message 异常提示信息
     * @param cause   根因异常
     */
    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }


    /**
     * 构造带消息的异常。
     *
     * @param message 异常提示信息
     */
    public SmsException(String message) {
        super(message);
    }


    /**
     * 构造带根因的异常。
     *
     * @param cause 根因异常
     */
    public SmsException(Throwable cause) {
        super(cause);
    }
}
