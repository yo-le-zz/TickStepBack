package dev.yolezz.tickstepback.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.MinecraftServer;

import java.util.logging.Logger;

/**
 * SCOPE OF THIS FILE - READ BEFORE USING
 * =======================================
 * This is a starting skeleton, NOT a port of the Paper plugin's rollback
 * engine. It currently does exactly two things:
 *
 *   1. On every server tick, logs the vanilla tick-freeze state
 *      (server.getTickManager().isFrozen() / isSteppingForward(), or the
 *      equivalent accessor for your target mappings - see the note below)
 *      so you can confirm this mod correctly observes the same vanilla
 *      /tick freeze + /tick step state the Paper plugin reads via
 *      ServerTickManager. /tick freeze and /tick step are VANILLA
 *      commands since 1.20.3 (not a Paper addition), so they exist on a
 *      Fabric server too - a mod, unlike a Bukkit plugin, has direct
 *      access to that internal state.
 *   2. Registers an empty `/tickstepback` command stub that currently
 *      just reports "not implemented yet" - it does NOT undo anything.
 *
 * What is NOT here, and why it's a real chunk of separate work, not a
 * quick follow-up: the Paper plugin's actual value is BlockChangeTracker/
 * EntityChangeTracker (capturing before/after BlockState via Bukkit
 * events) and RollbackManager (silent restore + settle pass). On Fabric
 * there is no Bukkit event system to hook - the equivalent would be Mixins
 * into the block-setting code path (roughly `World#setBlockState` /
 * `ServerWorld` internals) plus the same ring-buffer/delta architecture
 * already in common/src/main/java, ported to vanilla types
 * (net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState)
 * instead of org.bukkit ones. That's a substantial, separate implementation
 * effort, not something safe to fake here.
 *
 * IMPORTANT ON METHOD NAMES: TickRateManager's exact method names
 * (isFrozen/setFrozen/stepGameIfPaused/isSprinting or their Yarn-mapped
 * equivalents) can and do shift between mapping releases. Verify them
 * against the Yarn mappings for your exact minecraft_version (see
 * gradle.properties) before compiling - I have not been able to verify
 * them against a real mappings browser or a real build from this
 * environment (no network access to maven.fabricmc.net here).
 */
public final class TickStepBackFabric implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("TickStepBack");

    @Override
    public void onInitializeServer() {
        LOGGER.info("TickStepBack (Fabric, squelette initial - voir mod-fabric/README.md) charge.");

        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);

        CommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void onEndTick(MinecraftServer server) {
        try {
            var tickManager = server.getTickManager(); // verify against your Yarn mappings - name may differ
            boolean frozen = tickManager.isFrozen();
            boolean stepping = tickManager.isSteppingForward();
            if (frozen || stepping) {
                LOGGER.fine("[TickStepBack] tick=" + server.getTicks() + " frozen=" + frozen + " stepping=" + stepping);
            }
        } catch (Throwable t) {
            // Deliberately defensive: this whole method is a diagnostic
            // stub, it must never be able to crash the server tick loop.
            LOGGER.warning("[TickStepBack] impossible de lire l'etat tick-freeze (verifiez les mappings): " + t);
        }
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.server.command.ServerCommandSource> dispatcher,
                                   CommandRegistryAccess registryAccess,
                                   net.minecraft.server.command.CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("tickstepback")
                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                                    "TickStepBack (Fabric) : le moteur de rollback n'est pas encore porte sur Fabric. "
                                            + "Voir mod-fabric/README.md."), false);
                            return 0;
                        })));
    }
}
