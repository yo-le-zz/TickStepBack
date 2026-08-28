package dev.yolezz.tickstepback.tracking;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Records a change to a non-player entity over one tick: either a
 * spawn, a despawn/removal, or a movement (position/rotation/velocity).
 *
 * Honest limitation (documented in README): this restores transform
 * (position, rotation, velocity) and existence (spawned/removed), plus
 * the handful of extra fields listed on {@link Kind#STATE_CHANGE} events
 * we specifically listen for (health via EntityDamageEvent/EntityRegainHealthEvent,
 * item-frame/armor-stand pose where Bukkit exposes it). It does NOT restore
 * arbitrary internal AI/behavior state (pathfinding targets, brain memory,
 * potion effect internals beyond what EntityPotionEffectEvent exposes,
 * villager reputation, etc.) because Paper's public API does not expose a
 * generic "serialize this entity's full state" primitive for living
 * entities the way it does for blocks via BlockData/BlockState.
 */
public final class EntityDelta {

    public enum Kind { SPAWN, REMOVE, TRANSFORM, STATE_CHANGE }

    private final UUID entityId;
    private final EntityType entityType;
    private final Kind kind;
    private final Location before; // null for SPAWN
    private final Location after;  // null for REMOVE
    private final double beforeHealth;
    private final double afterHealth;

    public EntityDelta(UUID entityId, EntityType entityType, Kind kind,
                        Location before, Location after,
                        double beforeHealth, double afterHealth) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.kind = kind;
        this.before = before;
        this.after = after;
        this.beforeHealth = beforeHealth;
        this.afterHealth = afterHealth;
    }

    public UUID entityId() {
        return entityId;
    }

    public EntityType entityType() {
        return entityType;
    }

    public Kind kind() {
        return kind;
    }

    public Location before() {
        return before;
    }

    public Location after() {
        return after;
    }

    public double beforeHealth() {
        return beforeHealth;
    }

    public double afterHealth() {
        return afterHealth;
    }

    public int estimateBytes() {
        return 96;
    }
}
