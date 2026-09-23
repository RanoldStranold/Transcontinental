package net.ranold.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

public final class RailDustParticles {

    private RailDustParticles() {
    }

    private static final float PARTICLE_DENSITY = 0.2F;

    public static void animate(final Level level, final RandomSource random, final BlockPos pos,
                               final RailShape shape, final int power) {
        if (power == 0) {
            return;
        }

        final int color = RedstoneWireBlock.getColorForPower(power);

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            switch (sideFor(shape, direction)) {
                case UP:
                    spawnAlongLine(level, random, pos, color, direction, Direction.UP, -0.5F, 0.5F);
                case SIDE:
                    spawnAlongLine(level, random, pos, color, Direction.DOWN, direction, 0.0F, 0.5F);
                    break;
                case NONE:
                default:
                    spawnAlongLine(level, random, pos, color, Direction.DOWN, direction, 0.0F, 0.3F);
            }
        }
    }

    private static RedstoneSide sideFor(final RailShape shape, final Direction direction) {
        final Direction uphill = switch (shape) {
            case ASCENDING_EAST -> Direction.EAST;
            case ASCENDING_WEST -> Direction.WEST;
            case ASCENDING_NORTH -> Direction.NORTH;
            case ASCENDING_SOUTH -> Direction.SOUTH;
            default -> null;
        };

        if (direction == uphill) {
            return RedstoneSide.UP;
        }

        final boolean connected = switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH ->
                    direction == Direction.NORTH || direction == Direction.SOUTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST ->
                    direction == Direction.EAST || direction == Direction.WEST;
            case NORTH_EAST -> direction == Direction.NORTH || direction == Direction.EAST;
            case NORTH_WEST -> direction == Direction.NORTH || direction == Direction.WEST;
            case SOUTH_EAST -> direction == Direction.SOUTH || direction == Direction.EAST;
            case SOUTH_WEST -> direction == Direction.SOUTH || direction == Direction.WEST;
        };

        return connected ? RedstoneSide.SIDE : RedstoneSide.NONE;
    }

    private static void spawnAlongLine(final Level level, final RandomSource random, final BlockPos pos,
                                       final int color, final Direction perpendicular, final Direction along,
                                       final float start, final float end) {
        final float length = end - start;

        if (random.nextFloat() >= PARTICLE_DENSITY * length) {
            return;
        }

        final float offset = start + length * random.nextFloat();
        final double x = 0.5D + 0.4375F * perpendicular.getStepX() + offset * along.getStepX();
        final double y = 0.5D + 0.4375F * perpendicular.getStepY() + offset * along.getStepY();
        final double z = 0.5D + 0.4375F * perpendicular.getStepZ() + offset * along.getStepZ();

        level.addParticle(new DustParticleOptions(color, 1.0F),
                pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0.0D, 0.0D, 0.0D);
    }
}
