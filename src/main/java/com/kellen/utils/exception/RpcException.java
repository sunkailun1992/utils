package com.kellen.utils.exception;

/**
 * RPC 调用异常。
 *
 * @author 孙凯伦
 */
public class RpcException extends RuntimeException {
    /**
     * 构造无消息的异常。
     */
    public RpcException() {
        super();
    }

    /**
     * 构造带消息与根因的异常。
     *
     * @param message 异常提示信息
     * @param cause   根因异常
     */
    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }


    /**
     * 构造带消息的异常。
     *
     * @param message 异常提示信息
     */
    public RpcException(String message) {
        super(message);
    }


    /**
     * 构造带根因的异常。
     *
     * @param cause 根因异常
     */
    public RpcException(Throwable cause) {
        super(cause);
    }
}
