package com.gb.utils.exception;

/**
 * 发送短信异常
 * @author sunkailun
 * @DateTime 2020/12/31  上午9:35
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class SmsException extends RuntimeException {
    public SmsException() {
        super();
    }


    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }


    public SmsException(String message) {
        super(message);
    }


    public SmsException(Throwable cause) {
        super(cause);
    }
}
