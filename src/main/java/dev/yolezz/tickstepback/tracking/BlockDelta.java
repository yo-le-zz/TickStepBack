package dev.yolezz.tickstepback.tracking;

import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

/**
 * Records the state of a single block position immediately before and
 * immediately after a tick, for a position that was touched during that
 * tick.
 *
 * We snapshot full {@link BlockData} (not just the Material) so that
 * redstone-relevant sub-states are captured: repeater delay/locked,
 * comparator mode, piston extended/facing, observer powered, rail shape,
 * door open/hinge, etc. BlockData equality/toString round-trips through the
 * block data string format Minecraft itself uses for /setblock, which is
 * the same representation used to restore it.
 *
 * We additionally capture the raw NBT of the block entity, when present,
 * via {@link BlockState#getSnapshot()} + its NBT-bearing subtype (e.g.
 * Container, Sign, Skull...). Bukkit's BlockState already encapsulates this
 * for the types it models; for anything Bukkit does not model (rare, custom
 * NMS-only tile data) it is out of reach of the plugin API - see
 * nms/NmsCompatibility.java.
 */
public final class BlockDelta {

    private final long worldUid1, worldUid2; // split UUID to avoid extra allocation churn
    private final String worldName;
    private final int x, y, z;
    private final BlockData before;
    private final BlockData after;
    private final BlockState beforeState;
    private final BlockState afterState;

    public BlockDelta(String worldName, long worldUid1, long worldUid2, int x, int y, int z,
                       BlockData before, BlockData after,
                       BlockState beforeState, BlockState afterState) {
        this.worldName = worldName;
        this.worldUid1 = worldUid1;
        this.worldUid2 = worldUid2;
        this.x = x;
        this.y = y;
        this.z = z;
        this.before = before;
        this.after = after;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockData before() {
        return before;
    }

    public BlockData after() {
        return after;
    }

    public BlockState beforeState() {
        return beforeState;
    }

    public BlockState afterState() {
        return afterState;
    }

    /** True if before == after (net no-op over the tick); such deltas are dropped before storage. */
    public boolean isNoOp() {
        return before.matches(after) && beforeState.getClass().equals(afterState.getClass());
    }

    /** Rough heap estimate in bytes, used for /tickstepback status reporting. */
    public int estimateBytes() {
        // BlockData strings + a flat per-block-entity overhead guess.
        return 96 + before.getAsString().length() + after.getAsString().length();
    }
}
