package dev.yolezz.tickstepback.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void pushWithinCapacityKeepsEverything() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        for (int i = 0; i < 5; i++) {
            assertNull(rb.pushNewest(i));
        }
        assertEquals(5, rb.size());
        assertEquals(0, rb.evictedCount());
    }

    @Test
    void pushBeyondCapacityEvictsOldest() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        rb.pushNewest(1);
        rb.pushNewest(2);
        rb.pushNewest(3);
        Integer evicted = rb.pushNewest(4);
        assertEquals(1, evicted);
        assertEquals(3, rb.size());
        assertEquals(1, rb.evictedCount());
        // Oldest remaining should now be 2.
        assertEquals(2, rb.peekOldest());
        assertEquals(4, rb.peekNewest());
    }

    @Test
    void popNewestRemovesInLifoOrder() {
        RingBuffer<Integer> rb = new RingBuffer<>(10);
        rb.pushNewest(1);
        rb.pushNewest(2);
        rb.pushNewest(3);
        assertEquals(3, rb.popNewest());
        assertEquals(2, rb.popNewest());
        assertEquals(1, rb.popNewest());
        assertNull(rb.popNewest());
    }

    @Test
    void iteratesNewestToOldest() {
        RingBuffer<Integer> rb = new RingBuffer<>(10);
        rb.pushNewest(1);
        rb.pushNewest(2);
        rb.pushNewest(3);
        List<Integer> order = new ArrayList<>();
        rb.iterator().forEachRemaining(order::add);
        assertEquals(List.of(3, 2, 1), order);
    }

    @Test
    void shrinkingCapacityEvictsOldestImmediately() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        for (int i = 0; i < 5; i++) {
            rb.pushNewest(i);
        }
        rb.setCapacity(2);
        assertEquals(2, rb.size());
        assertEquals(3, rb.evictedCount());
        assertEquals(3, rb.peekOldest());
        assertEquals(4, rb.peekNewest());
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(0));
    }

    @Test
    void simulatesStepbackWindow_stepThenStepBackMatchesExample() {
        // Mirrors the spec's example: /tick step 50 then stepback 15 should
        // land on the state after the first 30 ticks (50 - 20). Here we model
        // "state" as the running sum of tick deltas and check that undoing
        // the last 20 of 50 pushed ticks reproduces the sum after 30 ticks.
        RingBuffer<Integer> rb = new RingBuffer<>(200);
        int runningSum = 0;
        List<Integer> sumAfterEachTick = new ArrayList<>();
        for (int tick = 1; tick <= 50; tick++) {
            runningSum += tick; // arbitrary deterministic per-tick delta
            rb.pushNewest(runningSum);
            sumAfterEachTick.add(runningSum);
        }
        // Undo the last 20 ticks (equivalent to "/tick stepback 20").
        for (int i = 0; i < 20; i++) {
            rb.popNewest();
        }
        int expectedAfter30Ticks = sumAfterEachTick.get(29); // 0-indexed: state after tick 30
        assertEquals(expectedAfter30Ticks, rb.peekNewest());
    }
}
