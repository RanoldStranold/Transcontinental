package net.ranold.rail;

import net.minecraft.world.level.block.PoweredRailBlock;
import net.ranold.block.IronPoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.ranold.registry.TCBlocks;

public final class RailSpeeds {

    private RailSpeeds() {
    }

    public static final double VANILLA_MAX = 0.4D;

    public static final double GOLD_MAX = 1.75D;

    public static final double GOLD_ACCEL = 0.00765625D;

    public static final double IRON_ACCEL = 0.06D;

    public static final double STANDSTILL_KICK = 0.2D;

    public static final double MOVING_THRESHOLD = 0.01D;

    public static final double WATER_FACTOR = 0.5D;

    public static final double HALT_THRESHOLD = 0.03D;

    public static final double HALT_FACTOR = 0.5D;

    public static final int MAX_POWER = 15;

    public static boolean isModRail(final BlockState state) {
        return state.is(TCBlocks.IRON_POWERED_RAIL) || state.is(TCBlocks.GOLD_POWERED_RAIL);
    }

    public static boolean isPowered(final BlockState state) {
        return state.getValue(PoweredRailBlock.POWERED);
    }

    public static double ironCap(final BlockState state) {
        return VANILLA_MAX * state.getValue(IronPoweredRailBlock.POWER) / MAX_POWER;
    }

    public static boolean holdsSpeed(final BlockState state) {
        if (!isModRail(state) || !isPowered(state)) {
            return false;
        }

        return !state.is(TCBlocks.IRON_POWERED_RAIL)
                || state.getValue(IronPoweredRailBlock.POWER) >= MAX_POWER;
    }
}
