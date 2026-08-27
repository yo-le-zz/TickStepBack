package dev.yolezz.tickstepback.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin typed wrapper around config.yml. Re-read on {@link #reload(JavaPlugin)}.
 */
public final class PluginConfig {

    public enum RollbackPhysicsMode { SETTLE, IMMEDIATE }

    private int historyTicks;
    private boolean trackBlocks;
    private boolean trackBlockEntities;
    private boolean trackEntities;
    private boolean redstonePowerOnly;
    private boolean autoCheckpoint;
    private int maxCheckpoints;
    private int maxBlockChangesPerTick;
    private boolean debugLogging;
    private RollbackPhysicsMode rollbackPhysicsMode;

    private PluginConfig() {
    }

    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        PluginConfig cfg = new PluginConfig();
        cfg.reload(plugin);
        return cfg;
    }

    public void reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        historyTicks = Math.max(1, c.getInt("history-ticks", 200));
        trackBlocks = c.getBoolean("tracking.blocks", true);
        trackBlockEntities = c.getBoolean("tracking.block-entities", true);
        trackEntities = c.getBoolean("tracking.entities", true);
        redstonePowerOnly = c.getBoolean("tracking.redstone-power-only", false);
        autoCheckpoint = c.getBoolean("auto-checkpoint", true);
        maxCheckpoints = Math.max(1, c.getInt("max-checkpoints", 10));
        maxBlockChangesPerTick = Math.max(100, c.getInt("max-block-changes-per-tick", 20000));
        debugLogging = c.getBoolean("debug-logging", false);

        String modeRaw = c.getString("rollback-physics-mode", "settle");
        if (modeRaw != null && modeRaw.equalsIgnoreCase("immediate")) {
            rollbackPhysicsMode = RollbackPhysicsMode.IMMEDIATE;
        } else {
            if (modeRaw != null && !modeRaw.equalsIgnoreCase("settle")) {
                plugin.getLogger().warning("rollback-physics-mode invalide ('" + modeRaw
                        + "'), valeurs acceptees: settle, immediate. Utilisation de 'settle'.");
            }
            rollbackPhysicsMode = RollbackPhysicsMode.SETTLE;
        }
    }

    public int historyTicks() {
        return historyTicks;
    }

    public boolean trackBlocks() {
        return trackBlocks;
    }

    public boolean trackBlockEntities() {
        return trackBlockEntities;
    }

    public boolean trackEntities() {
        return trackEntities;
    }

    public boolean redstonePowerOnly() {
        return redstonePowerOnly;
    }

    public boolean autoCheckpoint() {
        return autoCheckpoint;
    }

    public int maxCheckpoints() {
        return maxCheckpoints;
    }

    public int maxBlockChangesPerTick() {
        return maxBlockChangesPerTick;
    }

    public boolean debugLogging() {
        return debugLogging;
    }

    public RollbackPhysicsMode rollbackPhysicsMode() {
        return rollbackPhysicsMode;
    }
}

