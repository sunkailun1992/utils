package com.kellen.utils.exception;

import com.kellen.utils.enumeration.ReturnCode;
import lombok.Getter;

/**
 * 用户登录异常
 * @author 孙凯伦
 * 
 */
public class UserException extends RuntimeException {

    @Getter
    private ReturnCode returnCode;

    /**
     * 构造无消息的异常。
     */
    public UserException() {
        super();
    }

    /**
     * 构造带统一错误码与消息的异常。
     *
     * @param returnCode 统一错误码
     * @param message    异常提示信息
     */
    public UserException(ReturnCode returnCode, String message) {
        super(message);
        this.returnCode = returnCode;
    }

    /**
     * 构造带消息与根因的异常。
     *
     * @param message 异常提示信息
     * @param cause   根因异常
     */
    public UserException(String message, Throwable cause) {
        super(message, cause);
    }


    /**
     * 构造带消息的异常。
     *
     * @param message 异常提示信息
     */
    public UserException(String message) {
        super(message);
    }


    /**
     * 构造带根因的异常。
     *
     * @param cause 根因异常
     */
    public UserException(Throwable cause) {
        super(cause);
    }
}
