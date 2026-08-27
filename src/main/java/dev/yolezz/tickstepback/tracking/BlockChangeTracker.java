package dev.yolezz.tickstepback.tracking;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.tick.TickTracker;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Watches a broad set of Bukkit/Paper block-related events, and for every
 * position that is touched during a history-worthy tick, captures a
 * before/after {@link BlockState} pair (diffed at end-of-tick, see class
 * javadoc on why we diff instead of trusting each event's own before/after
 * fields).
 *
 * Coverage and known gaps are documented in README.md ("Ce qui est
 * restaurable"). In short: any block mutation that goes through one of the
 * Bukkit events registered below is captured; anything that changes a
 * block purely inside NMS without ever firing a corresponding Bukkit event
 * (rare, but e.g. some sculk sensor / vibration bookkeeping, or vanilla
 * random block ticks that don't fire a Bukkit event on some versions) is
 * NOT captured and is called out explicitly in the README rather than
 * silently pretending to be complete.
 */
public final class BlockChangeTracker implements Listener {

    private final TickStepBackPlugin plugin;
    private final TickTracker tickTracker;

    /** Position key -> BlockState captured the first time that position was touched this tick. */
    private final Map<String, BlockState> dirtyThisTick = new LinkedHashMap<>();
    private final Map<String, Block> dirtyBlocks = new HashMap<>();

    public BlockChangeTracker(TickStepBackPlugin plugin, TickTracker tickTracker) {
        this.plugin = plugin;
        this.tickTracker = tickTracker;
    }

    private boolean suspended() {
        return tickTracker.isRollbackInProgress() || !tickTracker.isRecording() || !plugin.config().trackBlocks();
    }

    // ------------------------------------------------------------------
    // Dirty tracking
    // ------------------------------------------------------------------

    private void markDirty(Block block) {
        if (block == null || suspended()) {
            return;
        }
        String key = posKey(block);
        dirtyThisTick.computeIfAbsent(key, k -> block.getState());
        dirtyBlocks.putIfAbsent(key, block);
    }

    private static String posKey(Block block) {
        // Includes world name so the same x/y/z in two different worlds
        // (e.g. overworld and nether) never collide within the same tick.
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    // ------------------------------------------------------------------
    // Placement / breaking
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        markDirty(e.getBlock());
        markDirty(e.getSource());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent e) {
        markDirty(e.getBlock());
        markDirty(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent e) {
        markDirty(e.getBlock());
    }

    // ------------------------------------------------------------------
    // Redstone / mechanisms - the core of this plugin's use case
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        markDirty(e.getBlock());
        for (Block moved : e.getBlocks()) {
            markDirty(moved);
            markDirty(moved.getRelative(e.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        markDirty(e.getBlock());
        for (Block moved : e.getBlocks()) {
            markDirty(moved);
            markDirty(moved.getRelative(e.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRedstone(BlockRedstoneEvent e) {
        if (plugin.config().redstonePowerOnly()) {
            // User asked to skip this (very high-frequency) event and rely
            // only on structural events. Wire power level then only gets
            // captured incidentally if something else also marks the wire.
            return;
        }
        if (e.getOldCurrent() == e.getNewCurrent()) {
            return;
        }
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        markDirty(e.getBlock());
    }

    // ------------------------------------------------------------------
    // Containers / block-entity contents
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onHopperMove(InventoryMoveItemEvent e) {
        markIfBlockHolder(e.getSource());
        markIfBlockHolder(e.getDestination());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        markIfBlockHolder(e.getInventory());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFurnaceBurn(FurnaceBurnEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFurnaceSmelt(FurnaceSmeltEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent e) {
        markDirty(e.getBlock());
    }

    private void markIfBlockHolder(Inventory inv) {
        if (inv == null || !plugin.config().trackBlockEntities()) {
            return;
        }
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof Container container) {
            markDirty(container.getBlock());
        } else if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof Container left) {
                markDirty(left.getBlock());
            }
            if (doubleChest.getRightSide() instanceof Container right) {
                markDirty(right.getBlock());
            }
        }
    }

    // ------------------------------------------------------------------
    // Entity-caused block changes and explosions
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        markDirty(e.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            markDirty(b);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        markDirty(e.getBlock());
        for (Block b : e.blockList()) {
            markDirty(b);
        }
    }

    // ------------------------------------------------------------------
    // End of tick: diff every touched position and hand deltas to TickTracker
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTickEnd(ServerTickEndEvent event) {
        if (dirtyThisTick.isEmpty()) {
            return;
        }
        if (tickTracker.isRollbackInProgress() || !tickTracker.isRecording()) {
            dirtyThisTick.clear();
            dirtyBlocks.clear();
            return;
        }

        int max = plugin.config().maxBlockChangesPerTick();
        int emitted = 0;
        for (Map.Entry<String, BlockState> entry : dirtyThisTick.entrySet()) {
            if (emitted >= max) {
                plugin.getLogger().warning("TickStepBack: plus de " + max
                        + " changements de blocs en un seul tick, le surplus n'est pas enregistre "
                        + "(voir max-block-changes-per-tick dans config.yml).");
                break;
            }
            Block block = dirtyBlocks.get(entry.getKey());
            if (block == null) {
                continue;
            }
            BlockState before = entry.getValue();
            BlockState after = block.getState();
            boolean isBlockEntity = after instanceof TileState;
            if (isBlockEntity && !plugin.config().trackBlockEntities()) {
                continue;
            }
            if (!isBlockEntity && !plugin.config().trackBlocks()) {
                continue;
            }

            World world = block.getWorld();
            BlockDelta delta = new BlockDelta(
                    world.getName(),
                    world.getUID().getMostSignificantBits(),
                    world.getUID().getLeastSignificantBits(),
                    block.getX(), block.getY(), block.getZ(),
                    before.getBlockData(), after.getBlockData(),
                    before, after
            );
            if (!delta.isNoOp()) {
                tickTracker.recordBlockChange(delta);
                emitted++;
            }
        }
        dirtyThisTick.clear();
        dirtyBlocks.clear();
    }
}
