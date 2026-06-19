package com.kellen.utils.redisson;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁操作接口。
 *
 * <p>公共调用方通过该接口屏蔽 Redisson 细节；业务代码必须保证加锁和解锁成对出现，
 * 并为长任务选择合适租约时间，避免无界锁影响其它租户或任务。</p>
 */
public interface Locker {
    /**
     * 获取锁，如果锁不可用，则当前线程处于休眠状态，直到获得锁为止。
     *
     * @param lockKey 锁 key
     */
    void lock(String lockKey);

    /**
     * 释放锁。
     *
     * @param lockKey 锁 key
     */
    void unlock(String lockKey);

    /**
     * 按秒获取带租约的锁，租约到期后自动释放。
     *
     * @param lockKey 锁 key
     * @param timeout 租约秒数
     */
    void lock(String lockKey, int timeout);

    /**
     * 按指定时间单位获取带租约的锁，租约到期后自动释放。
     *
     * @param lockKey 锁 key
     * @param unit    租约时间单位
     * @param timeout 租约时长
     */
    void lock(String lockKey, TimeUnit unit, int timeout);

    /**
     * 立即尝试获取锁，不等待。
     *
     * @param lockKey 锁 key
     * @return true 表示获取成功，false 表示锁已被占用
     */
    boolean tryLock(String lockKey);

    /**
     * 在等待时间内尝试获取锁，获取成功后按租约自动释放。
     *
     * @param lockKey   锁 key
     * @param waitTime  等待获取锁的最长时间
     * @param leaseTime 获取成功后的租约时间
     * @param unit      时间单位
     * @return true 表示获取成功，false 表示等待超时仍未获取
     * @throws InterruptedException 等待锁时线程被中断
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 判断锁是否被任意线程持有。
     *
     * @param lockKey 锁 key
     * @return true 表示锁当前被持有
     */
    boolean isLocked(String lockKey);
}
