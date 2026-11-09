package com.kellen.observability.fleet;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** 只复制已消费/已写出的字节，溢出后释放前缀，不聚合流或改变消费行为。 */
final class BodyCapture {
    private final int limit;
    private final ByteArrayOutputStream buffer;
    private long bytes;
    private boolean overflow;
    private boolean discarded;
    private boolean complete;

    BodyCapture(int limit) { this.limit = limit; this.buffer = new ByteArrayOutputStream(Math.min(limit, 256)); }
    synchronized void add(byte[] value, int offset, int length, boolean retain) {
        bytes = bytes > Long.MAX_VALUE - length ? Long.MAX_VALUE : bytes + length;
        if (!retain) { discard(); return; }
        if (discarded || overflow) return;
        if (buffer.size() + (long) length > limit) { overflow = true; buffer.reset(); return; }
        buffer.write(value, offset, length);
    }
    synchronized void add(ByteBuffer value, boolean retain) {
        ByteBuffer duplicate = value.asReadOnlyBuffer();
        int length = duplicate.remaining();
        if (!retain || length > limit || buffer.size() + (long) length > limit) {
            bytes = bytes > Long.MAX_VALUE - length ? Long.MAX_VALUE : bytes + length;
            if (!retain) discard(); else { overflow = true; buffer.reset(); }
            return;
        }
        byte[] data = new byte[length]; duplicate.get(data); add(data, 0, length, true);
    }
    synchronized void discard() { discarded = true; buffer.reset(); }
    synchronized void reset() { buffer.reset(); bytes = 0; overflow = false; discarded = false; complete = false; }
    synchronized byte[] snapshot() { return overflow ? new byte[limit + 1] : buffer.toByteArray(); }
    synchronized long bytes() { return bytes; }
    synchronized boolean complete() { return complete; }
    synchronized void completed() { complete = true; }
}
