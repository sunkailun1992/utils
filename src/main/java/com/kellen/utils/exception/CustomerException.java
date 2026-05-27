package com.kellen.utils.exception;

import lombok.Getter;

/**
 * 客户模块异常处理
 *
 * @author 孙凯伦
 * @date 2022/4/1 2:06 下午
 */
public class CustomerException extends RuntimeException {

    @Getter
    private String errorCode;

    public CustomerException() {
        super();
    }

    public CustomerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CustomerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CustomerException(String errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public CustomerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String errorCode) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = errorCode;
    }
}
