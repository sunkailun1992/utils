package com.kellen.utils.exception;

/**
 * @ClassName RpcException
 * @Description rpc调用异常
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/4/6 9:57 上午
 */
public class RpcException extends RuntimeException {
    public RpcException() {
        super();
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }


    public RpcException(String message) {
        super(message);
    }


    public RpcException(Throwable cause) {
        super(cause);
    }
}
