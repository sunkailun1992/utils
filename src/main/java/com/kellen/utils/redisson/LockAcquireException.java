package com.kellen.utils.redisson;

/**
 * 在等待时间内没有拿到分布式锁，或等待被中断时抛出。
 *
 * @author 孙凯伦
 */
public class LockAcquireException extends RuntimeException {

    /**
     * 等待超时没有拿到锁。
     *
     * @param lockKey 锁键
     */
    public LockAcquireException(String lockKey) {
        super("未能在等待时间内获取分布式锁：" + lockKey);
    }

    /**
     * 等待锁时被中断。
     *
     * @param lockKey 锁键
     * @param cause   中断异常
     */
    public LockAcquireException(String lockKey, InterruptedException cause) {
        super("等待分布式锁时被中断：" + lockKey, cause);
    }
}
