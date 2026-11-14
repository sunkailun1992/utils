package com.kellen.utils.redisson;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
        if (lock.isHeldByCurrentThread()) { // 只释放当前线程持有的锁，租约到期或被他人持有时不抛 IllegalMonitorStateException。
            lock.unlock();
        }
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


    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, TimeUnit unit, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime, unit); // 不传租约，由 Redisson 看门狗续期，与 mcp 现有用法一致。
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); // 恢复中断标记，交给调用方决定是否继续。
            throw new LockAcquireException(lockKey, exception);
        }
        if (!acquired) {
            throw new LockAcquireException(lockKey);
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) { // 只释放自己持有的锁。
                lock.unlock();
            }
        }
    }

    @Override
    public void runWithLock(String lockKey, long waitTime, TimeUnit unit, Runnable action) {
        executeWithLock(lockKey, waitTime, unit, () -> {
            action.run();
            return null;
        });
    }
}
