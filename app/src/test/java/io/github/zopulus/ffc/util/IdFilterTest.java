package io.github.zopulus.ffc.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class IdFilterTest {
    @Test public void returnsSameArrayWhenNothingIsExcluded() {
        int[] ids = {10001, 10002};
        assertSame(ids, IdFilter.without(ids, id -> false));
    }

    @Test public void removesOnlyExcludedIdsAndKeepsOrder() {
        int[] ids = {10001, 10269, 10002, 10269, 10003};
        assertArrayEquals(new int[]{10001, 10002, 10003}, IdFilter.without(ids, id -> id == 10269));
        assertArrayEquals(new int[]{10001, 10269, 10002, 10269, 10003}, ids);
    }

    @Test public void canExcludeEverything() {
        assertEquals(0, IdFilter.without(new int[]{1, 2}, id -> true).length);
    }

    @Test public void nullAndEmptyAreUnchanged() {
        assertNull(IdFilter.without(null, id -> true));
        int[] empty = {};
        assertSame(empty, IdFilter.without(empty, id -> true));
    }

    @Test public void predicateIsEvaluatedOncePerId() {
        int[] calls = {0};
        IdFilter.without(new int[]{1, 2, 3}, id -> { calls[0]++; return id == 2; });
        assertEquals(3, calls[0]);
    }
}
