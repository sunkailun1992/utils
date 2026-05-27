package com.kellen.utils.exception;

import com.kellen.utils.enumeration.ReturnCode;
import lombok.Getter;

/**
 * Created with IntelliJ IDEA.
 * 用户登录异常
 * @author 孙凯伦
 * @DateTime 2020/12/10  下午9:08
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class UserException extends RuntimeException {

    @Getter
    private ReturnCode returnCode;

    public UserException() {
        super();
    }

    public UserException(ReturnCode returnCode, String message) {
        super(message);
        this.returnCode = returnCode;
    }

    public UserException(String message, Throwable cause) {
        super(message, cause);
    }


    public UserException(String message) {
        super(message);
    }


    public UserException(Throwable cause) {
        super(cause);
    }
}
