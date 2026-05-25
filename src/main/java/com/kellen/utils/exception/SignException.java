package com.kellen.utils.exception;

/**
 * @author: ranyang
 * @Date: 2021/3/15 15:30
 * @descript: 业务异常
 */
public class SignException extends RuntimeException{

    public SignException() {
        super();
    }


    public SignException(String message, Throwable cause) {
        super(message, cause);
    }

    public SignException(String message) {
        super(message);
    }


    public SignException(Throwable cause) {
        super(cause);
    }

}
