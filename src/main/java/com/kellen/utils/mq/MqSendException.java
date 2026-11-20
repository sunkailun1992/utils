package com.kellen.utils.mq;

/**
 * RocketMQ 同步发送失败，或 broker 没有返回 {@code SEND_OK}。
 */
public class MqSendException extends RuntimeException {

    /**
     * 只有说明，没有原始异常。
     *
     * @param message 说明
     */
    public MqSendException(String message) {
        super(message);
    }

    /**
     * 带原始异常。
     *
     * @param message 说明
     * @param cause   原始异常
     */
    public MqSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
