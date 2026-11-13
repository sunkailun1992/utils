package com.kellen.traffic;

/**
 * Keeps a verified grant scoped to the current HTTP or RPC operation.
 */
public final class CanaryContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private CanaryContext() {
    }

    public static Snapshot get() {
        return CURRENT.get();
    }

    public static void set(Snapshot snapshot) {
        if (snapshot == null) {
            clear();
        } else {
            CURRENT.set(snapshot);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Snapshot(CanaryGrant grant, String token) {
    }
}
