package com.kellen.utils.redisson;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author 孙凯伦
 * 
 */
public class LockUtil {

    private static Locker locker;

    /**
     * 设置工具类使用的locker
     *
     * @param locker
     */
    public static void setLocker(Locker locker) {
        LockUtil.locker = locker;
    }

    /**
     * 获取锁
     *
     * @param lockKey
     */
    public static void lock(String lockKey) {
        locker.lock(lockKey);
    }

    /**
     * 释放锁
     *
     * @param lockKey
     */
    public static void unlock(String lockKey) {
        locker.unlock(lockKey);
    }

    /**
     * 获取锁，超时释放
     *
     * @param lockKey
     * @param timeout
     */
    public static void lock(String lockKey, int timeout) {
        locker.lock(lockKey, timeout);
    }

    /**
     * 获取锁，超时释放，指定时间单位
     *
     * @param lockKey
     * @param unit
     * @param timeout
     */
    public static void lock(String lockKey, TimeUnit unit, int timeout) {
        locker.lock(lockKey, unit, timeout);
    }

    /**
     * 尝试获取锁，获取到立即返回true,获取失败立即返回false
     *
     * @param lockKey
     * @return
     */
    public static boolean tryLock(String lockKey) {
        return locker.tryLock(lockKey);
    }

    /**
     * 尝试获取锁，在给定的waitTime时间内尝试，获取到返回true,获取失败返回false,获取到后再给定的leaseTime时间超时释放
     *
     * @param lockKey
     * @param waitTime
     * @param leaseTime
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public static boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return locker.tryLock(lockKey, waitTime, leaseTime, unit);
    }

    /**
     * 锁释放被任意一个线程持有
     *
     * @param lockKey
     * @return
     */
    public static boolean isLocked(String lockKey) {
        return locker.isLocked(lockKey);
    }

    /**
     * 在等待时间内加锁并执行操作，执行完释放自己持有的锁；不传租约，由 Redisson 看门狗续期。
     *
     * @param lockKey  锁键
     * @param waitTime 最长等待时间
     * @param unit     等待时间单位
     * @param action   持锁期间执行的操作
     * @param <T>      操作返回值类型
     * @return 操作返回值
     * @throws LockAcquireException 等待超时或等待被中断
     */
    public static <T> T executeWithLock(String lockKey, long waitTime, TimeUnit unit, Supplier<T> action) {
        return locker.executeWithLock(lockKey, waitTime, unit, action);
    }

    /**
     * 无返回值版本的 {@link #executeWithLock(String, long, TimeUnit, Supplier)}。
     *
     * @param lockKey  锁键
     * @param waitTime 最长等待时间
     * @param unit     等待时间单位
     * @param action   持锁期间执行的操作
     * @throws LockAcquireException 等待超时或等待被中断
     */
    public static void runWithLock(String lockKey, long waitTime, TimeUnit unit, Runnable action) {
        locker.runWithLock(lockKey, waitTime, unit, action);
    }
}
