package dev.yolezz.tickstepback.tick;

import dev.yolezz.tickstepback.util.RingBuffer;

import java.util.Iterator;

/**
 * Holds the sliding window of the last N history-worthy ticks.
 *
 * This is intentionally a thin wrapper: all the bounded-memory logic lives
 * in {@link RingBuffer}, which is unit tested independently of Bukkit.
 */
public final class TickHistory {

    private final RingBuffer<TickDelta> ring;

    public TickHistory(int historyTicks) {
        this.ring = new RingBuffer<>(historyTicks);
    }

    public void setCapacity(int historyTicks) {
        ring.setCapacity(historyTicks);
    }

    public void record(TickDelta delta) {
        ring.pushNewest(delta);
    }

    /** Removes and returns the most recently recorded tick, or null if the history is empty. */
    public TickDelta popNewest() {
        return ring.popNewest();
    }

    public int availableTicks() {
        return ring.size();
    }

    public void clear() {
        ring.clear();
    }

    public long totalEvictedTicks() {
        return ring.evictedCount();
    }

    public long estimateMemoryBytes() {
        long total = 0;
        for (TickDelta d : ring) {
            total += d.estimateBytes();
        }
        return total;
    }

    public long totalChangeCount() {
        long total = 0;
        for (TickDelta d : ring) {
            total += d.changeCount();
        }
        return total;
    }

    public Iterator<TickDelta> newestToOldest() {
        return ring.iterator();
    }

    public TickDelta peekNewest() {
        return ring.peekNewest();
    }
}
