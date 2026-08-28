package dev.yolezz.tickstepback.command;

import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.rollback.CheckpointManager;
import dev.yolezz.tickstepback.rollback.RollbackManager;
import dev.yolezz.tickstepback.rollback.RollbackResult;
import dev.yolezz.tickstepback.tick.TickHistory;
import dev.yolezz.tickstepback.tick.TickTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class TickStepBackCommand implements CommandExecutor, TabCompleter {

    private final TickStepBackPlugin plugin;
    private final TickTracker tickTracker;
    private final RollbackManager rollbackManager;
    private final CheckpointManager checkpointManager;

    public TickStepBackCommand(TickStepBackPlugin plugin, TickTracker tickTracker,
                                RollbackManager rollbackManager, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.tickTracker = tickTracker;
        this.rollbackManager = rollbackManager;
        this.checkpointManager = checkpointManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tickstepback.use")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission tickstepback.use.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "clear" -> handleClear(sender);
            case "checkpoint" -> handleCheckpoint(sender);
            default -> handleStepback(sender, args[0]);
        }
        return true;
    }

    private void handleStepback(CommandSender sender, String rawTicks) {
        long ticks;
        try {
            ticks = Long.parseLong(rawTicks);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Nombre de ticks invalide: " + rawTicks, NamedTextColor.RED));
            return;
        }
        if (ticks <= 0) {
            sender.sendMessage(Component.text("Le nombre de ticks doit etre superieur a 0.", NamedTextColor.RED));
            return;
        }

        RollbackResult result = rollbackManager.rollback(ticks);
        NamedTextColor color = switch (result.status()) {
            case OK -> NamedTextColor.GREEN;
            case PARTIAL -> NamedTextColor.YELLOW;
            default -> NamedTextColor.RED;
        };
        sender.sendMessage(Component.text("[TickStepBack] " + result.status() + " - "
                + result.achievedTicks() + "/" + result.requestedTicks() + " tick(s) restaure(s), "
                + result.blockChangesApplied() + " bloc(s), "
                + result.entityTransformsApplied() + " entite(s) deplacee(s), "
                + result.entitySpawnsUndone() + " entite(s) retirees, "
                + result.entityRemovalsSkipped() + " suppression(s) d'entite non restaurables.", color));
        for (String msg : result.messages()) {
            sender.sendMessage(Component.text("  - " + msg, NamedTextColor.GRAY));
        }
    }

    private void handleStatus(CommandSender sender) {
        TickHistory history = tickTracker.history();
        long bytes = history.estimateMemoryBytes();
        sender.sendMessage(Component.text("=== TickStepBack status ===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Ticks disponibles pour rollback: " + history.availableTicks()
                + " / " + plugin.config().historyTicks(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Dernier tick execute (numero vanilla, cf. /tick query): "
                + tickTracker.lastCompletedTickId(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Changements enregistres: " + history.totalChangeCount(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Memoire approximative utilisee: " + formatBytes(bytes), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Ticks evinces depuis le demarrage (hors fenetre): " + history.totalEvictedTicks(), NamedTextColor.GRAY));
        var tm = org.bukkit.Bukkit.getServerTickManager();
        sender.sendMessage(Component.text("Serveur fige (/tick freeze): " + tm.isFrozen()
                + (tm.isStepping() ? " (stepping)" : "") + (tm.isSprinting() ? " (sprinting)" : ""), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Mode de reactivation physique du rollback: "
                + plugin.config().rollbackPhysicsMode(), NamedTextColor.GRAY));
    }

    private void handleClear(CommandSender sender) {
        if (!sender.hasPermission("tickstepback.admin")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission tickstepback.admin.", NamedTextColor.RED));
            return;
        }
        tickTracker.history().clear();
        sender.sendMessage(Component.text("Historique TickStepBack vide.", NamedTextColor.YELLOW));
    }

    private void handleCheckpoint(CommandSender sender) {
        if (!sender.hasPermission("tickstepback.admin")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission tickstepback.admin.", NamedTextColor.RED));
            return;
        }
        try {
            var path = checkpointManager.writeCheckpoint(tickTracker.history(), tickTracker.sessionId());
            sender.sendMessage(Component.text("Checkpoint ecrit: " + path, NamedTextColor.GREEN));
        } catch (Exception ex) {
            sender.sendMessage(Component.text("Echec du checkpoint: " + ex, NamedTextColor.RED));
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage: /" + label + " <ticks|status|checkpoint|clear>", NamedTextColor.YELLOW));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KiB", bytes / 1024.0);
        return String.format("%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("1", "10", "20", "100", "status", "checkpoint", "clear").stream()
                    .filter(s -> s.startsWith(args[0]))
                    .toList();
        }
        return List.of();
    }
}
