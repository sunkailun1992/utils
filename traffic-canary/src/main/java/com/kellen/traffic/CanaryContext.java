package com.kellen.traffic;

import java.util.function.Supplier;

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

    /** Binds only during a synchronous action on the actual execution thread. */
    public static <T> T callWith(Snapshot snapshot, Supplier<T> action) {
        Snapshot previous = get();
        set(snapshot);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    public record Snapshot(CanaryGrant grant, String token) {
    }
}
