package com.gb.utils.exception;

/**
 * @author: ranyang
 * @Date: 2021/3/15 15:30
 * @descript: 业务异常
 */
public class BusinessException extends RuntimeException{

    public BusinessException() {
        super();
    }


    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }


    public BusinessException(String message) {
        super(message);
    }


    public BusinessException(Throwable cause) {
        super(cause);
    }

}
