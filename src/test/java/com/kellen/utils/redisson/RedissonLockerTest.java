package com.kellen.utils.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 按 Redisson 主流用法验证安全解锁与回调式加锁：只释放自己持有的锁，加锁不传租约由看门狗续期。
 */
class RedissonLockerTest {

    private static final String KEY = "lock:sample";

    private RLock lock;
    private RedissonLocker locker;

    @BeforeEach
    void setUp() {
        RedissonClient client = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(client.getLock(KEY)).thenReturn(lock);
        locker = new RedissonLocker(client);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void unlockSkipsWhenNotHeldByCurrentThread() {
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        locker.unlock(KEY);

        verify(lock, never()).unlock();
    }

    @Test
    void unlockReleasesWhenHeldByCurrentThread() {
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        locker.unlock(KEY);

        verify(lock, times(1)).unlock();
    }

    @Test
    void executeWithLockRunsActionThenReleases() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = locker.executeWithLock(KEY, 10, TimeUnit.SECONDS, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock, times(1)).unlock();
    }

    @Test
    void executeWithLockReleasesWhenActionThrows() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> locker.executeWithLock(KEY, 10, TimeUnit.SECONDS, () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        verify(lock, times(1)).unlock();
    }

    @Test
    void executeWithLockThrowsWhenNotAcquired() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        assertThrows(LockAcquireException.class,
                () -> locker.runWithLock(KEY, 10, TimeUnit.SECONDS, () -> ran.set(true)));

        assertThat(ran).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void executeWithLockRestoresInterruptFlag() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException("stop"));

        assertThrows(LockAcquireException.class,
                () -> locker.executeWithLock(KEY, 10, TimeUnit.SECONDS, () -> "never"));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(lock, never()).unlock();
    }

    @Test
    void executeWithLockUsesWatchdog() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        locker.executeWithLock(KEY, 10, TimeUnit.SECONDS, () -> "done");

        verify(lock).tryLock(10, TimeUnit.SECONDS);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }
}
