package dev.yolezz.tickstepback.tracking;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.tick.TickTracker;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks non-player entity spawn/despawn/movement so that rollback can undo
 * "the machine launched an item frame / minecart / dropped item" style
 * side effects.
 *
 * By design (see README "Joueurs"), players are never tracked here: no
 * position, inventory, health or XP rollback is ever attempted for a
 * Player, even if track-entities is enabled. This is intentional, not an
 * oversight - see the class-level note in RollbackManager.
 *
 * Honest limitation: only spawn/remove/transform (position, rotation,
 * velocity) is captured. Internal entity brain/AI state is not - see
 * EntityDelta's javadoc.
 */
public final class EntityChangeTracker implements Listener {

    private final TickStepBackPlugin plugin;
    private final TickTracker tickTracker;

    /** Entities that spawned this tick - so a subsequent REMOVE in the same tick collapses to a no-op instead of two deltas. */
    private final Map<UUID, EntityDelta> pendingThisTick = new HashMap<>();

    public EntityChangeTracker(TickStepBackPlugin plugin, TickTracker tickTracker) {
        this.plugin = plugin;
        this.tickTracker = tickTracker;
    }

    private boolean suspended() {
        return tickTracker.isRollbackInProgress() || !tickTracker.isRecording() || !plugin.config().trackEntities();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent e) {
        if (suspended() || e.getEntity() instanceof Player) {
            return;
        }
        Entity entity = e.getEntity();
        pendingThisTick.compute(entity.getUniqueId(), (id, existing) -> new EntityDelta(
                id, entity.getType(), EntityDelta.Kind.SPAWN,
                null, entity.getLocation(),
                Double.NaN, currentHealth(entity)
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (suspended() || e.getEntity() instanceof Player) {
            return;
        }
        Entity entity = e.getEntity();
        UUID id = entity.getUniqueId();
        EntityDelta pendingSpawn = pendingThisTick.get(id);
        if (pendingSpawn != null && pendingSpawn.kind() == EntityDelta.Kind.SPAWN) {
            // Spawned and removed within the same history tick: net no-op.
            pendingThisTick.remove(id);
            return;
        }
        pendingThisTick.put(id, new EntityDelta(
                id, entity.getType(), EntityDelta.Kind.REMOVE,
                entity.getLocation(), null,
                currentHealth(entity), Double.NaN
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(EntityMoveEvent e) {
        if (suspended() || e.getEntity() instanceof Player) {
            return;
        }
        Entity entity = e.getEntity();
        UUID id = entity.getUniqueId();
        EntityDelta existing = pendingThisTick.get(id);
        if (existing != null && existing.kind() == EntityDelta.Kind.SPAWN) {
            // Already recording as a spawn this tick; the spawn location IS the after-state, leave it.
            return;
        }
        pendingThisTick.compute(id, (k, current) -> new EntityDelta(
                id, entity.getType(), EntityDelta.Kind.TRANSFORM,
                current != null ? current.before() : e.getFrom(),
                e.getTo(),
                currentHealth(entity), currentHealth(entity)
        ));
    }

    private double currentHealth(Entity entity) {
        if (entity instanceof org.bukkit.entity.LivingEntity le) {
            return le.getHealth();
        }
        return Double.NaN;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTickEnd(ServerTickEndEvent event) {
        if (pendingThisTick.isEmpty()) {
            return;
        }
        if (!tickTracker.isRollbackInProgress() && tickTracker.isRecording()) {
            for (EntityDelta delta : pendingThisTick.values()) {
                tickTracker.recordEntityChange(delta);
            }
        }
        pendingThisTick.clear();
    }
}
