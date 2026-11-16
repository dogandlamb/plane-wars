package edu.hitsz.sky.core;

import java.util.function.IntSupplier;

/** One allocator per battlefield; retired identities are never reused. */
final class EntityIds implements IntSupplier {
    private int next = 1;
    @Override public int getAsInt() {
        if (next == Integer.MAX_VALUE) throw new IllegalStateException("Battle entity IDs exhausted");
        return next++;
    }
}
