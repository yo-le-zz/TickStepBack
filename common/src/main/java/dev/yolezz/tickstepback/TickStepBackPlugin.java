package dev.yolezz.tickstepback;

import dev.yolezz.tickstepback.command.TickStepBackCommand;
import dev.yolezz.tickstepback.config.PluginConfig;
import dev.yolezz.tickstepback.rollback.CheckpointManager;
import dev.yolezz.tickstepback.rollback.RollbackManager;
import dev.yolezz.tickstepback.tick.TickHistory;
import dev.yolezz.tickstepback.tick.TickTracker;
import dev.yolezz.tickstepback.tracking.BlockChangeTracker;
import dev.yolezz.tickstepback.tracking.EntityChangeTracker;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TickStepBackPlugin extends JavaPlugin {

    private PluginConfig config;
    private TickHistory tickHistory;
    private TickTracker tickTracker;
    private BlockChangeTracker blockChangeTracker;
    private EntityChangeTracker entityChangeTracker;
    private RollbackManager rollbackManager;
    private CheckpointManager checkpointManager;

    @Override
    public void onEnable() {
        this.config = PluginConfig.load(this);
        this.tickHistory = new TickHistory(config.historyTicks());
        this.tickTracker = new TickTracker(this, tickHistory);
        this.blockChangeTracker = new BlockChangeTracker(this, tickTracker);
        this.entityChangeTracker = new EntityChangeTracker(this, tickTracker);
        this.checkpointManager = new CheckpointManager(this);
        this.rollbackManager = new RollbackManager(this, tickTracker, checkpointManager);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(tickTracker, this);
        pm.registerEvents(blockChangeTracker, this);
        pm.registerEvents(entityChangeTracker, this);

        TickStepBackCommand commandExecutor = new TickStepBackCommand(this, tickTracker, rollbackManager, checkpointManager);
        registerCommand("tickstepback", commandExecutor);
        registerCommand("tsb", commandExecutor);

        getLogger().info("TickStepBack active. Fenetre d'historique: " + config.historyTicks() + " ticks.");
    }

    private void registerCommand(String name, TickStepBackCommand executor) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("La commande '" + name + "' n'a pas pu etre enregistree (plugin.yml).");
        }
    }

    @Override
    public void onDisable() {
        if (tickHistory != null) {
            tickHistory.clear();
        }
    }

    public void reloadPluginConfig() {
        config.reload(this);
        tickHistory.setCapacity(config.historyTicks());
    }

    public PluginConfig config() {
        return config;
    }

    public TickTracker tickTracker() {
        return tickTracker;
    }

    public RollbackManager rollbackManager() {
        return rollbackManager;
    }
}
