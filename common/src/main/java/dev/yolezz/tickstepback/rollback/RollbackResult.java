package dev.yolezz.tickstepback.rollback;

import java.util.ArrayList;
import java.util.List;

public final class RollbackResult {

    public enum Status { OK, PARTIAL, REFUSED, EMPTY_HISTORY, ERROR }

    private Status status = Status.OK;
    private long requestedTicks;
    private long achievedTicks;
    private int blockChangesApplied;
    private int entityTransformsApplied;
    private int entitySpawnsUndone;
    private int entityRemovalsSkipped;
    private final List<String> messages = new ArrayList<>();

    public Status status() {
        return status;
    }

    public void status(Status status) {
        this.status = status;
    }

    public long requestedTicks() {
        return requestedTicks;
    }

    public void requestedTicks(long v) {
        this.requestedTicks = v;
    }

    public long achievedTicks() {
        return achievedTicks;
    }

    public void achievedTicks(long v) {
        this.achievedTicks = v;
    }

    public int blockChangesApplied() {
        return blockChangesApplied;
    }

    public void addBlockChangeApplied() {
        blockChangesApplied++;
    }

    public int entityTransformsApplied() {
        return entityTransformsApplied;
    }

    public void addEntityTransformApplied() {
        entityTransformsApplied++;
    }

    public int entitySpawnsUndone() {
        return entitySpawnsUndone;
    }

    public void addEntitySpawnUndone() {
        entitySpawnsUndone++;
    }

    public int entityRemovalsSkipped() {
        return entityRemovalsSkipped;
    }

    public void addEntityRemovalSkipped() {
        entityRemovalsSkipped++;
    }

    public List<String> messages() {
        return messages;
    }

    public void addMessage(String message) {
        messages.add(message);
    }
}
