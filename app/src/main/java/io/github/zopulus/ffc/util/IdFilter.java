package io.github.zopulus.ffc.util;

import java.util.function.IntPredicate;

/** Removes protected ids from a freeze request without touching the caller's array. */
public final class IdFilter {
    private IdFilter() {}

    /** Returns {@code ids} itself when nothing is excluded, otherwise a new array without the excluded ids. */
    public static int[] without(int[] ids, IntPredicate excluded) {
        if (ids == null) return null;
        int keep = 0;
        boolean[] drop = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            drop[i] = excluded.test(ids[i]);
            if (!drop[i]) keep++;
        }
        if (keep == ids.length) return ids;
        int[] filtered = new int[keep];
        int next = 0;
        for (int i = 0; i < ids.length; i++) if (!drop[i]) filtered[next++] = ids[i];
        return filtered;
    }
}
