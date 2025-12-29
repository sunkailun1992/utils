package com.gb.utils.exception;

/**
 * Created with IntelliJ IDEA.
 * 参数为空异常
 * @author sunkailun
 * @DateTime 2020/12/10  下午9:28
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class ParameterNullException extends RuntimeException {

    public ParameterNullException() {
        super();
    }


    public ParameterNullException(String message, Throwable cause) {
        super(message, cause);
    }


    public ParameterNullException(String message) {
        super(message);
    }


    public ParameterNullException(Throwable cause) {
        super(cause);
    }
}
