package net.ranold.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RailShape;

public class IronPoweredRailBlock extends PoweredRailBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private static final int MAX_SEARCH_DEPTH = 8;

    public IronPoweredRailBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
        RailDustParticles.animate(level, random, pos, state.getValue(SHAPE), state.getValue(POWER));
    }

    @Override
    protected void updateState(final BlockState state, final Level level, final BlockPos pos, final Block block) {
        final int best = Math.max(level.getBestNeighborSignal(pos),
                Math.max(searchSignal(level, pos, state, true, 0),
                        searchSignal(level, pos, state, false, 0)));

        final boolean powered = best > 0;

        if (state.getValue(POWER) == best && state.getValue(POWERED) == powered) {
            return;
        }

        level.setBlockAndUpdate(pos, state.setValue(POWER, best).setValue(POWERED, powered));
        level.updateNeighborsAt(pos.below(), this);

        if (state.getValue(SHAPE).isSlope()) {
            level.updateNeighborsAt(pos.above(), this);
        }
    }

    private int searchSignal(final Level level, final BlockPos pos, final BlockState state,
                             final boolean forward, final int depth) {
        if (depth >= MAX_SEARCH_DEPTH) {
            return 0;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean checkBelow = true;
        RailShape shape = state.getValue(SHAPE);

        switch (shape) {
            case NORTH_SOUTH -> z += forward ? 1 : -1;
            case EAST_WEST -> x += forward ? -1 : 1;
            case ASCENDING_EAST -> {
                if (forward) {
                    x--;
                } else {
                    x++;
                    y++;
                    checkBelow = false;
                }
                shape = RailShape.EAST_WEST;
            }
            case ASCENDING_WEST -> {
                if (forward) {
                    x--;
                    y++;
                    checkBelow = false;
                } else {
                    x++;
                }
                shape = RailShape.EAST_WEST;
            }
            case ASCENDING_NORTH -> {
                if (forward) {
                    z++;
                } else {
                    z--;
                    y++;
                    checkBelow = false;
                }
                shape = RailShape.NORTH_SOUTH;
            }
            case ASCENDING_SOUTH -> {
                if (forward) {
                    z++;
                    y++;
                    checkBelow = false;
                } else {
                    z--;
                }
                shape = RailShape.NORTH_SOUTH;
            }
            default -> {
                return 0;
            }
        }

        final int direct = signalAt(level, new BlockPos(x, y, z), forward, depth, shape);

        if (direct > 0 || !checkBelow) {
            return direct;
        }

        return signalAt(level, new BlockPos(x, y - 1, z), forward, depth, shape);
    }

    private int signalAt(final Level level, final BlockPos pos, final boolean forward,
                         final int depth, final RailShape approach) {
        final BlockState state = level.getBlockState(pos);

        if (!state.is(this)) {
            return 0;
        }

        final RailShape shape = state.getValue(SHAPE);

        if (approach == RailShape.EAST_WEST
                && (shape == RailShape.NORTH_SOUTH
                || shape == RailShape.ASCENDING_NORTH
                || shape == RailShape.ASCENDING_SOUTH)) {
            return 0;
        }

        if (approach == RailShape.NORTH_SOUTH
                && (shape == RailShape.EAST_WEST
                || shape == RailShape.ASCENDING_EAST
                || shape == RailShape.ASCENDING_WEST)) {
            return 0;
        }

        return Math.max(level.getBestNeighborSignal(pos),
                this.searchSignal(level, pos, state, forward, depth + 1));
    }
}
