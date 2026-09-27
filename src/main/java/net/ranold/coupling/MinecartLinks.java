package net.ranold.coupling;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class MinecartLinks {

    public static final int SLOTS = 2;

    private final @Nullable UUID[] partners = new UUID[SLOTS];

    private final int[] ends = new int[SLOTS];

    private final int[] missingTicks = new int[SLOTS];

    private final int[] holdTicks = new int[SLOTS];

    private long solvedTick = Long.MIN_VALUE;

    private Vec3 pendingBias = Vec3.ZERO;

    public @Nullable UUID partner(final int slot) {
        return this.partners[slot];
    }

    public void setPartner(final int slot, final @Nullable UUID partner) {
        this.partners[slot] = partner;
        this.ends[slot] = 0;
        this.missingTicks[slot] = 0;
        this.holdTicks[slot] = 0;
    }

    public boolean isHolding() {
        return this.holdTicks[0] > 0 || this.holdTicks[1] > 0;
    }

    public void hold(final int slot, final int ticks) {
        this.holdTicks[slot] = ticks;
    }

    public void release(final int slot) {
        this.holdTicks[slot] = 0;
    }

    public void tickHold() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.holdTicks[slot] > 0) {
                this.holdTicks[slot]--;
            }
        }
    }

    public int end(final int slot) {
        return this.ends[slot];
    }

    public void setEnd(final int slot, final int end) {
        this.ends[slot] = Integer.signum(end);
    }

    public int slotOf(final UUID partner) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (partner.equals(this.partners[slot])) {
                return slot;
            }
        }

        return -1;
    }

    public int freeSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.partners[slot] == null) {
                return slot;
            }
        }

        return -1;
    }

    public boolean isEmpty() {
        return this.partners[0] == null && this.partners[1] == null;
    }

    public int tickMissing(final int slot) {
        return ++this.missingTicks[slot];
    }

    public void clearMissing(final int slot) {
        this.missingTicks[slot] = 0;
    }

    public Vec3 pendingBias() {
        return this.pendingBias;
    }

    public void setPendingBias(final Vec3 bias) {
        this.pendingBias = bias;
    }

    public long solvedTick() {
        return this.solvedTick;
    }

    public void setSolvedTick(final long tick) {
        this.solvedTick = tick;
    }
}
