package dev.yolezz.tickstepback.tick;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.tracking.BlockDelta;
import dev.yolezz.tickstepback.tracking.EntityDelta;
import org.bukkit.Bukkit;
import org.bukkit.ServerTickManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Detects which server ticks are "history-worthy" and owns the currently
 * open {@link TickDelta}.
 *
 * How tick detection actually works on Paper 1.21.10
 * ---------------------------------------------------
 * Paper exposes {@link ServerTickManager} (Bukkit.getServer().getServerTickManager())
 * with isFrozen()/isStepping()/stepGameIfFrozen(int). The vanilla /tick freeze
 * and /tick step commands are thin wrappers around exactly this object - there
 * is no other, more "authoritative" state to read.
 *
 * Paper also fires {@link ServerTickStartEvent} and {@link ServerTickEndEvent}
 * once per iteration of the main server tick loop, unconditionally - this loop
 * keeps running even while frozen (it still has to process network packets,
 * commands, chat, etc. at ~20Hz), but the *world* tick (level.tick(), which is
 * what actually moves redstone, pistons, entities...) is skipped whenever the
 * server is frozen and not currently stepping.
 *
 * We therefore treat a server tick as history-worthy - i.e. "the world is
 * really about to advance" - exactly when, at ServerTickStartEvent time:
 *
 *     !tickManager.isFrozen()  OR  tickManager.isStepping()
 *
 * that is: either the server is running normally, or it is frozen but a
 * /tick step (or stepGameIfFrozen) is actively in progress. This matches the
 * requirement to never fabricate history while sitting frozen and idle, and
 * to faithfully capture every tick a /tick step actually advances.
 *
 * We do not attempt to distinguish "vanilla /tick step command" from "some
 * other plugin/mod calling stepGameIfFrozen()" - isStepping() does not
 * differentiate the caller, and there is no such distinction in vanilla
 * either.
 *
 * Tick numbering - use vanilla's own counter, not a self-maintained one
 * -----------------------------------------------------------------------
 * Earlier versions of this plugin kept a private {@code tickCounter} field,
 * incremented once per history-worthy tick. That is an unnecessary
 * reinvention: {@link ServerTickStartEvent#getTickNumber()} already exposes
 * the server's real, authoritative tick number (the same counter vanilla's
 * own {@code /tick query} reports, "first tick = 1", monotonically
 * increasing for the whole server lifetime, frozen ticks included). Any
 * private counter can drift from that value - for instance if this
 * listener were ever registered twice, or if a future Paper version fires
 * these events at a different cadence than assumed. Using
 * {@code getTickNumber()} directly removes that whole class of bug by
 * construction: our tick ids ARE vanilla's tick ids, so "N ticks back" in
 * /tickstepback always means exactly N real Minecraft ticks, with no
 * separate conversion factor to get wrong.
 */
public final class TickTracker implements Listener {

    private final TickStepBackPlugin plugin;
    private final TickHistory history;

    private TickDelta currentDelta;
    private boolean recordingThisTick;
    private long lastCompletedTickId = -1;
    private boolean wasFrozen = false;
    private long sessionId = 0;

    /** true while RollbackManager is actively undoing changes; trackers must ignore/tag events during this window. */
    private volatile boolean rollbackInProgress = false;

    public TickTracker(TickStepBackPlugin plugin, TickHistory history) {
        this.plugin = plugin;
        this.history = history;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickStart(ServerTickStartEvent event) {
        if (rollbackInProgress) {
            // Rollback drives its own synthetic bookkeeping; never open a
            // history tick while we are actively undoing one.
            recordingThisTick = false;
            currentDelta = null;
            return;
        }

        ServerTickManager tickManager = Bukkit.getServerTickManager();
        boolean frozenNow = tickManager.isFrozen();
        if (frozenNow && !wasFrozen) {
            // Rising edge of /tick freeze: a new debug session begins, so the
            // next rollback should take a fresh auto-checkpoint.
            sessionId++;
        }
        wasFrozen = frozenNow;

        boolean worldWillTick = !frozenNow || tickManager.isStepping();
        recordingThisTick = worldWillTick;

        if (recordingThisTick) {
            currentDelta = new TickDelta(event.getTickNumber(), System.currentTimeMillis());
        } else {
            currentDelta = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (rollbackInProgress) {
            return;
        }
        if (currentDelta != null) {
            lastCompletedTickId = currentDelta.tickId();
            if (!currentDelta.isEmpty()) {
                history.record(currentDelta);
                if (plugin.config().debugLogging()) {
                    plugin.getLogger().info("[tick " + currentDelta.tickId() + "] recorded "
                            + currentDelta.changeCount() + " change(s).");
                }
            }
            // Empty ticks are intentionally NOT stored - see TickDelta javadoc.
            // This keeps "no-op" frozen/idle-ish ticks from wasting ring buffer slots
            // once redstone settles between two /tick step bursts.
        }
        currentDelta = null;
    }

    public boolean isRecording() {
        return recordingThisTick && currentDelta != null;
    }

    public void recordBlockChange(BlockDelta delta) {
        if (isRecording() && !delta.isNoOp()) {
            currentDelta.addBlockChange(delta);
        }
    }

    public void recordEntityChange(EntityDelta delta) {
        if (isRecording()) {
            currentDelta.addEntityChange(delta);
        }
    }

    public boolean isRollbackInProgress() {
        return rollbackInProgress;
    }

    public void setRollbackInProgress(boolean inProgress) {
        this.rollbackInProgress = inProgress;
    }

    public TickHistory history() {
        return history;
    }

    public long currentTickId() {
        return lastCompletedTickId + 1;
    }

    /** Id (vanilla tick number) of the most recently completed history-worthy tick, or -1 if none has happened yet. */
    public long lastCompletedTickId() {
        return lastCompletedTickId;
    }

    /** Increments on every rising edge of /tick freeze; used to trigger one auto-checkpoint per debug session. */
    public long sessionId() {
        return sessionId;
    }
}
