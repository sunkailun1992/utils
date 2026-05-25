package com.kellen.utils.exception;

/**
 * Created with IntelliJ IDEA.
 * 版本异常
 * @author sunkailun
 * @DateTime 2020/12/10  下午9:08
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class VersionException extends RuntimeException {
    public VersionException() {
        super();
    }


    public VersionException(String message, Throwable cause) {
        super(message, cause);
    }


    public VersionException(String message) {
        super(message);
    }


    public VersionException(Throwable cause) {
        super(cause);
    }
}
