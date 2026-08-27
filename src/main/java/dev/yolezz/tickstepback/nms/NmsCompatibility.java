package dev.yolezz.tickstepback.nms;

/**
 * TickStepBack does NOT currently use any net.minecraft/NMS/internal-Paper
 * code. Everything is implemented against the public Paper/Bukkit API
 * (org.bukkit.*, com.destroystokyo.paper.*, io.papermc.paper.*).
 *
 * This class exists as the designated, isolated home for NMS code IF a
 * future version needs it, per the project's requirement that any
 * internals access be isolated in dedicated classes rather than scattered
 * through the codebase. Below is a precise account of the two places where
 * the public API currently falls short, why, and what an NMS-based
 * extension would look like - written so a maintainer can pick this up
 * without re-deriving the investigation.
 *
 * 1) "/tick stepback" as a literal subcommand of vanilla "/tick"
 *    ------------------------------------------------------------
 *    Paper's /tick command is a vanilla Brigadier command owned by the
 *    "minecraft" namespace and rebuilt by the server on (re)load. Bukkit's
 *    plugin.yml `commands` section can only register new root command
 *    labels (here: /tickstepback); it cannot graft a new literal argument
 *    node onto an existing vanilla command tree. Doing that for real
 *    requires reaching into the Brigadier CommandDispatcher
 *    (net.minecraft.commands.CommandDispatcher /
 *    com.mojang.brigadier.CommandDispatcher) after vanilla registers "tick"
 *    and injecting a child LiteralCommandNode - which means depending on
 *    Paper/Mojang-mapped internals that can and do change every Minecraft
 *    version, and risks fighting the server's own command sync packets on
 *    reload. That is a meaningfully higher maintenance/compatibility risk
 *    than the rest of this plugin for a purely cosmetic win (the same
 *    functionality is one word longer as "/tickstepback 50" vs
 *    "/tick stepback 50"), so it was deliberately NOT done. If it's ever
 *    wanted, this is the file where that Brigadier injection would live.
 *
 * 2) Full internal entity state (beyond transform + health)
 *    --------------------------------------------------------
 *    Bukkit models a block's full restorable state via BlockState/BlockData
 *    (which is why block rollback in this plugin is solid: capture +
 *    BlockState#update(true,false) round-trips essentially everything,
 *    including block-entity NBT). There is no equivalent generic
 *    "serialize this LivingEntity's full internal state" API for entities:
 *    AI goal/brain state, pathfinding targets, villager reputation/gossip,
 *    raid state, and similar are internal to net.minecraft.world.entity.*
 *    and not exposed by org.bukkit.entity.*. A full solution would mean
 *    reading/writing the entity's vanilla NBT compound directly via NMS
 *    (net.minecraft.nbt.CompoundTag + Entity#saveWithoutId /
 *    #load-equivalent), which is exactly the kind of version-fragile,
 *    mapping-dependent code this project wants isolated and clearly
 *    labelled rather than silently relied upon. Until implemented, this
 *    plugin restores entity spawn/despawn/position/rotation/velocity/health
 *    only - see EntityDelta's javadoc and README "Limitations".
 */
public final class NmsCompatibility {
    private NmsCompatibility() {
    }
}
