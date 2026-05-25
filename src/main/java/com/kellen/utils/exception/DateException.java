package com.kellen.utils.exception;

/**
 * @ClassName DateException
 * @Description 时间参数异常
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/8/6 10:14 上午
 */
public class DateException extends RuntimeException{

    public DateException() {
        super();
    }


    public DateException(String message, Throwable cause) {
        super(message, cause);
    }


    public DateException(String message) {
        super(message);
    }


    public DateException(Throwable cause) {
        super(cause);
    }

}
