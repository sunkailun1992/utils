package com.gb.utils.exception;

/**
 * Created with IntelliJ IDEA.
 * 幂等异常
 * @author sunkailun
 * @DateTime 2020/12/10  下午9:08
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class PreventRepeatException extends RuntimeException {
    public PreventRepeatException() {
        super();
    }


    public PreventRepeatException(String message, Throwable cause) {
        super(message, cause);
    }


    public PreventRepeatException(String message) {
        super(message);
    }


    public PreventRepeatException(Throwable cause) {
        super(cause);
    }
}
