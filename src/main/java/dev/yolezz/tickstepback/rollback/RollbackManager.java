package dev.yolezz.tickstepback.rollback;

import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.tick.TickDelta;
import dev.yolezz.tickstepback.tick.TickHistory;
import dev.yolezz.tickstepback.tick.TickTracker;
import dev.yolezz.tickstepback.tracking.BlockDelta;
import dev.yolezz.tickstepback.config.PluginConfig;
import dev.yolezz.tickstepback.tracking.EntityDelta;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Executes a /tickstepback rollback: pops recorded ticks from the history
 * ring buffer (newest first) and re-applies each recorded "before" state,
 * until the requested number of real ticks has been undone or the
 * available history is exhausted.
 *
 * Concurrency / thread-safety: must be called from the main server thread
 * (Bukkit world mutation is not thread-safe), which is guaranteed for
 * command execution. {@link TickTracker#setRollbackInProgress(boolean)} is
 * toggled around the whole operation so that:
 *   - BlockChangeTracker/EntityChangeTracker ignore the edits WE make while
 *     undoing (rollbackInProgress = true suspends recording), preventing
 *     the rollback from polluting its own history.
 *   - block restoration happens in two passes (see {@link #rollback(long)}):
 *     first a "silent" pass (force=true, applyPhysics=false) that writes
 *     every position's final restored data without letting any single write
 *     trigger neighbor updates while the surrounding blocks are still only
 *     half-restored (which would otherwise cause spurious intermediate
 *     cascades based on an inconsistent, partially-rolled-back world) -
 *     then, once every position is in its correct final state, a "settle"
 *     pass that re-applies each position's now-correct data with
 *     applyPhysics=true, so redstone/pistons/observers get re-notified and
 *     resume normal propagation on subsequent ticks. Skipping this second
 *     pass is what used to leave the world stuck exactly in the
 *     post-rollback state: the data was correct but nothing was hooked back
 *     into the vanilla update/notify system, so nothing happened at the
 *     following ticks - not even after a further /tick step - until some
 *     unrelated event forced a neighbor update.
 */
public final class RollbackManager {

    private final TickStepBackPlugin plugin;
    private final TickTracker tickTracker;
    private final CheckpointManager checkpointManager;

    public RollbackManager(TickStepBackPlugin plugin, TickTracker tickTracker, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.tickTracker = tickTracker;
        this.checkpointManager = checkpointManager;
    }

    public RollbackResult rollback(long ticksRequested) {
        RollbackResult result = new RollbackResult();
        result.requestedTicks(ticksRequested);

        if (!Bukkit.isPrimaryThread()) {
            result.status(RollbackResult.Status.ERROR);
            result.addMessage("Rollback doit etre execute sur le thread principal.");
            return result;
        }
        if (ticksRequested <= 0) {
            result.status(RollbackResult.Status.REFUSED);
            result.addMessage("Le nombre de ticks doit etre superieur a 0.");
            return result;
        }
        if (tickTracker.isRollbackInProgress()) {
            result.status(RollbackResult.Status.REFUSED);
            result.addMessage("Un rollback est deja en cours, reessayez dans un instant.");
            return result;
        }

        TickHistory history = tickTracker.history();
        long lastTick = tickTracker.lastCompletedTickId();
        if (lastTick < 0 || history.availableTicks() == 0) {
            result.status(RollbackResult.Status.EMPTY_HISTORY);
            result.addMessage("Aucun historique disponible pour le moment (aucun tick execute depuis le demarrage du plugin).");
            return result;
        }

        long targetTickId = lastTick - ticksRequested + 1;

        if (checkpointManager.shouldCheckpoint(tickTracker.sessionId())) {
            try {
                checkpointManager.writeCheckpoint(history, tickTracker.sessionId());
            } catch (Exception ex) {
                result.addMessage("Avertissement: le checkpoint de securite a echoue (" + ex.getMessage() + "), rollback poursuivi.");
                plugin.getLogger().warning("Checkpoint failed: " + ex);
            }
        }

        tickTracker.setRollbackInProgress(true);
        logTickManagerState("avant rollback");
        long lastUndoneTickId = lastTick + 1; // sentinel: nothing undone yet
        Set<Block> touchedBlocks = new LinkedHashSet<>();
        boolean immediate = plugin.config().rollbackPhysicsMode() == PluginConfig.RollbackPhysicsMode.IMMEDIATE;
        try {
            while (history.availableTicks() > 0) {
                TickDelta newest = history.peekNewest();
                if (newest.tickId() < targetTickId) {
                    break;
                }
                history.popNewest();
                applyUndo(newest, result, touchedBlocks, immediate);
                lastUndoneTickId = newest.tickId();
            }
            if (!immediate) {
                // Second pass (default "settle" mode): now that every touched
                // position holds its final restored data, re-notify neighbors
                // so the world resumes normal propagation instead of sitting
                // frozen in the restored state. See class javadoc for why
                // this must be a separate pass in this mode.
                settlePhysics(touchedBlocks, result);
            }
        } catch (Exception ex) {
            result.status(RollbackResult.Status.ERROR);
            result.addMessage("Erreur pendant le rollback: " + ex + " - l'etat du monde peut etre partiellement restaure. "
                    + "Consultez le checkpoint le plus recent dans plugins/TickStepBack/checkpoints/.");
            plugin.getLogger().severe("Rollback error: " + ex);
            ex.printStackTrace();
        } finally {
            tickTracker.setRollbackInProgress(false);
            logTickManagerState("apres rollback");
        }

        long achieved = lastUndoneTickId <= lastTick ? (lastTick - lastUndoneTickId + 1) : 0;
        result.achievedTicks(achieved);

        if (result.status() == RollbackResult.Status.OK || result.status() == RollbackResult.Status.EMPTY_HISTORY) {
            if (achieved == 0) {
                result.status(RollbackResult.Status.EMPTY_HISTORY);
                result.addMessage("Aucun tick correspondant dans l'historique.");
            } else if (achieved < ticksRequested) {
                result.status(RollbackResult.Status.PARTIAL);
                result.addMessage("Historique insuffisant: seulement " + achieved + "/" + ticksRequested
                        + " ticks ont pu etre restaures (augmentez history-ticks dans config.yml si besoin).");
            } else {
                result.status(RollbackResult.Status.OK);
            }
        }
        return result;
    }

    private void applyUndo(TickDelta delta, RollbackResult result, Set<Block> touchedBlocks, boolean immediatePhysics) {
        for (BlockDelta bd : delta.blockChanges()) {
            try {
                World world = Bukkit.getWorld(bd.worldName());
                if (world == null) {
                    result.addMessage("Monde introuvable: " + bd.worldName() + " (bloc " + bd.x() + "," + bd.y() + "," + bd.z() + " ignore).");
                    continue;
                }
                Block block = world.getBlockAt(bd.x(), bd.y(), bd.z());
                // force=true: apply regardless of the block currently present.
                // applyPhysics: false in "settle" mode (default) - see class
                // javadoc; true in "immediate" mode (rollback-physics-mode:
                // immediate in config.yml), which notifies neighbors as soon
                // as each block is restored instead of deferring to a
                // separate pass. Offered as an alternative to help isolate a
                // persistent "world stuck after rollback" report that the
                // default settle pass alone did not resolve.
                bd.beforeState().update(true, immediatePhysics);
                touchedBlocks.add(block);
                result.addBlockChangeApplied();
                if (plugin.config().debugLogging()) {
                    plugin.getLogger().info("[rollback] bloc restaure (" + bd.worldName() + " " + bd.x() + ","
                            + bd.y() + "," + bd.z() + ") physics=" + immediatePhysics);
                }
            } catch (Exception ex) {
                result.addMessage("Echec restauration bloc (" + bd.x() + "," + bd.y() + "," + bd.z() + "): " + ex);
                plugin.getLogger().warning("[rollback] echec restauration bloc (" + bd.x() + "," + bd.y() + "," + bd.z() + "): " + ex);
            }
        }

        for (EntityDelta ed : delta.entityChanges()) {
            try {
                undoEntity(ed, result);
            } catch (Exception ex) {
                result.addMessage("Echec restauration entite " + ed.entityId() + ": " + ex);
            }
        }
    }

    /**
     * Re-applies each touched position's now-final data with
     * applyPhysics=true, so neighbors are notified and the world's normal
     * redstone/piston/observer update chain resumes from the restored
     * state. Still runs while rollbackInProgress is true, so any resulting
     * chain reaction is not itself recorded as new tick history - it is
     * treated as "reactivating" the restored past, not as a fresh change.
     *
     * We settle not only the touched blocks themselves but also their 6
     * direct neighbors. applyPhysics=true on a block mainly re-checks that
     * block's own placement validity and notifies adjacent blocks that
     * something around them changed - but comparators, repeaters and
     * redstone dust decide their own output by reading power from their
     * neighbors, so a neighbor that was NOT itself touched by the rollback
     * still needs an explicit nudge to re-evaluate against the freshly
     * restored block next to it. Without this, a restored block could sit
     * in the right state while an adjacent, unrestored comparator keeps
     * outputting whatever it last computed before the rollback, which is
     * indistinguishable from "the world is stuck" to an observer.
     */
    private void settlePhysics(Set<Block> touchedBlocks, RollbackResult result) {
        Set<Block> toSettle = new LinkedHashSet<>(touchedBlocks);
        for (Block block : touchedBlocks) {
            for (org.bukkit.block.BlockFace face : NEIGHBOR_FACES) {
                try {
                    toSettle.add(block.getRelative(face));
                } catch (Exception ex) {
                    // Defensive only: getRelative() can misbehave at world
                    // height bounds or on unloaded chunks in edge cases.
                    // Never let a single neighbor lookup abort the whole
                    // settle pass for every other block.
                    plugin.getLogger().warning("[rollback] impossible de resoudre un voisin de ("
                            + block.getX() + "," + block.getY() + "," + block.getZ() + ") face=" + face + ": " + ex);
                }
            }
        }
        int settled = 0;
        for (Block block : toSettle) {
            try {
                // Re-fetch the state we just wrote (not the historical
                // "before" snapshot) so we notify with the correct, final
                // data rather than re-writing anything.
                block.getState().update(true, true);
                settled++;
            } catch (Exception ex) {
                result.addMessage("Echec de la passe de reactivation physique en (" + block.getX() + ","
                        + block.getY() + "," + block.getZ() + "): " + ex);
                plugin.getLogger().warning("[rollback] echec settle physics (" + block.getX() + ","
                        + block.getY() + "," + block.getZ() + "): " + ex);
            }
        }
        if (plugin.config().debugLogging()) {
            plugin.getLogger().info("[rollback] passe de reactivation: " + settled + "/" + toSettle.size()
                    + " position(s) re-notifiees (blocs touches + voisins directs).");
        }
    }

    private void logTickManagerState(String when) {
        if (!plugin.config().debugLogging()) {
            return;
        }
        try {
            var tm = Bukkit.getServerTickManager();
            plugin.getLogger().info("[rollback] etat ServerTickManager " + when + ": isFrozen=" + tm.isFrozen()
                    + " isStepping=" + tm.isStepping() + " isSprinting=" + tm.isSprinting());
        } catch (Exception ex) {
            plugin.getLogger().warning("[rollback] impossible de lire ServerTickManager " + when + ": " + ex);
        }
    }

    private static final org.bukkit.block.BlockFace[] NEIGHBOR_FACES = {
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST
    };

    private void undoEntity(EntityDelta ed, RollbackResult result) {
        switch (ed.kind()) {
            case SPAWN -> {
                Entity entity = Bukkit.getEntity(ed.entityId());
                if (entity != null) {
                    entity.remove();
                    result.addEntitySpawnUndone();
                }
                // If already gone, nothing to do - the removal already happened naturally.
            }
            case REMOVE -> {
                // Honest limitation: Bukkit/Paper's public API offers no supported
                // way to respawn an entity with its exact prior UUID and full
                // internal state (equipment, AI, brain memory, passengers...).
                // We deliberately do not attempt a best-effort fake respawn that
                // would silently be wrong. See README "Limitations".
                result.addEntityRemovalSkipped();
            }
            case TRANSFORM, STATE_CHANGE -> {
                Entity entity = Bukkit.getEntity(ed.entityId());
                if (entity != null && ed.before() != null) {
                    entity.teleport(ed.before());
                    result.addEntityTransformApplied();
                }
            }
        }
    }
}
