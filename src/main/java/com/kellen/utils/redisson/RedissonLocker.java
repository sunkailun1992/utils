package com.kellen.utils.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁实现。
 *
 * <p>委托 {@link RedissonClient} 的 {@link org.redisson.api.RLock} 提供阻塞加锁、带租约加锁和尝试加锁；
 * 加锁与解锁必须成对出现，异常路径需保证释放。</p>
 *
 * @author 孙凯伦
 */
public class RedissonLocker implements Locker {

    private RedissonClient redissonClient;

    /**
     * @param redissonClient Redisson 客户端
     */
    public RedissonLocker(RedissonClient redissonClient) {
        super();
        this.redissonClient = redissonClient;
    }

    @Override
    public void lock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
    }

    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.unlock();
    }

    @Override
    public void lock(String lockKey, int leaseTime) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, TimeUnit.SECONDS);
    }

    @Override
    public void lock(String lockKey, TimeUnit unit, int timeout) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(timeout, unit);
    }

    @Override
    public boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.tryLock();
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

}