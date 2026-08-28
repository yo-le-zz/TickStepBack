package dev.yolezz.tickstepback.rollback;

import dev.yolezz.tickstepback.TickStepBackPlugin;
import dev.yolezz.tickstepback.tick.TickHistory;
import dev.yolezz.tickstepback.tracking.BlockDelta;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a lightweight, human-readable safety-net file before the first
 * rollback of a debug session (see TickTracker#sessionId()).
 *
 * IMPORTANT - what this checkpoint actually is:
 * it is NOT a full world backup. It only records, for every block position
 * currently present in the tick history, the OLDEST known "before" BlockData
 * string for that position within the current history window - i.e. the
 * state the position had right before history tracking started covering it.
 * This lets an operator manually /setblock things back with a command block
 * or datapack if a rollback goes wrong, without paying the cost (disk I/O,
 * memory, world-lock time) of a full save-all/world copy on every single
 * /tick stepback call.
 *
 * For an actual full-world safety net, take a normal Paper/Purpur world
 * backup (or run /save-all + copy the world folder) before starting a
 * destructive redstone debug session - this plugin deliberately does not
 * attempt to replace that.
 */
public final class CheckpointManager {

    private final TickStepBackPlugin plugin;
    private long lastCheckpointedSession = -1;

    public CheckpointManager(TickStepBackPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean shouldCheckpoint(long currentSessionId) {
        return plugin.config().autoCheckpoint() && currentSessionId != lastCheckpointedSession;
    }

    public Path writeCheckpoint(TickHistory history, long sessionId) {
        // Oldest known BlockData per position, first-write-wins while
        // scanning oldest -> newest so we keep the earliest recorded state.
        Map<String, BlockData> earliestByPos = new LinkedHashMap<>();
        List<dev.yolezz.tickstepback.tick.TickDelta> chronological = new ArrayList<>();
        history.newestToOldest().forEachRemaining(chronological::add);
        for (int i = chronological.size() - 1; i >= 0; i--) {
            for (BlockDelta bd : chronological.get(i).blockChanges()) {
                String key = bd.worldName() + ":" + bd.x() + ":" + bd.y() + ":" + bd.z();
                earliestByPos.putIfAbsent(key, bd.before());
            }
        }

        try {
            Path dir = plugin.getDataFolder().toPath().resolve("checkpoints");
            Files.createDirectories(dir);
            String stamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("checkpoint-" + stamp + "-session" + sessionId + ".txt");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
                out.println("# TickStepBack safety checkpoint");
                out.println("# session=" + sessionId + " generated=" + ZonedDateTime.now());
                out.println("# format: world;x;y;z;blockdata (use /setblock x y z <blockdata> to manually restore)");
                for (Map.Entry<String, BlockData> e : earliestByPos.entrySet()) {
                    String[] parts = e.getKey().split(":", 4);
                    out.println(parts[0] + ";" + parts[1] + ";" + parts[2] + ";" + parts[3] + ";" + e.getValue().getAsString());
                }
            }
            lastCheckpointedSession = sessionId;
            pruneOldCheckpoints(dir);
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void pruneOldCheckpoints(Path dir) throws IOException {
        int max = plugin.config().maxCheckpoints();
        try (var files = Files.list(dir)) {
            List<Path> sorted = files.filter(p -> p.getFileName().toString().startsWith("checkpoint-"))
                    .sorted()
                    .toList();
            int toDelete = sorted.size() - max;
            for (int i = 0; i < toDelete; i++) {
                Files.deleteIfExists(sorted.get(i));
            }
        }
    }
}
