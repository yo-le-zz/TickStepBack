package dev.yolezz.tickstepback.tick;

import dev.yolezz.tickstepback.tracking.BlockDelta;
import dev.yolezz.tickstepback.tracking.EntityDelta;

import java.util.ArrayList;
import java.util.List;

/**
 * All recorded changes that happened during a single "history-worthy"
 * server tick (i.e. a tick where the world actually ticked: either normal
 * play, or a tick produced by /tick step while frozen).
 *
 * A TickDelta with zero entries still occupies a slot in the ring buffer
 * (it represents "nothing changed this tick", which matters for counting
 * how many ticks to step back through) but costs next to no memory.
 */
public final class TickDelta {

    private final long tickId;
    private final long timestampMillis;
    private final List<BlockDelta> blockChanges = new ArrayList<>();
    private final List<EntityDelta> entityChanges = new ArrayList<>();
    private boolean truncated = false;

    public TickDelta(long tickId, long timestampMillis) {
        this.tickId = tickId;
        this.timestampMillis = timestampMillis;
    }

    public long tickId() {
        return tickId;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public void addBlockChange(BlockDelta delta) {
        blockChanges.add(delta);
    }

    public void addEntityChange(EntityDelta delta) {
        entityChanges.add(delta);
    }

    public List<BlockDelta> blockChanges() {
        return blockChanges;
    }

    public List<EntityDelta> entityChanges() {
        return entityChanges;
    }

    public boolean isEmpty() {
        return blockChanges.isEmpty() && entityChanges.isEmpty();
    }

    public void markTruncated() {
        this.truncated = true;
    }

    /** True if this tick hit max-block-changes-per-tick and some changes were dropped (best-effort rollback only). */
    public boolean isTruncated() {
        return truncated;
    }

    public int changeCount() {
        return blockChanges.size() + entityChanges.size();
    }

    public long estimateBytes() {
        long total = 64;
        for (BlockDelta b : blockChanges) total += b.estimateBytes();
        for (EntityDelta e : entityChanges) total += e.estimateBytes();
        return total;
    }
}
