package net.ranold.coupling;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RailPath {

    private RailPath() {
    }

    private static final int MAX_BLOCKS = 10;

    private static final int[] VERTICAL_SEARCH = {0, 1, -1};

    public record Position(BlockPos pos, Vec3i exit0, Vec3i exit1, double length, double t) {

        Vec3i exit(final int index) {
            return index == 0 ? this.exit0 : this.exit1;
        }
    }

    public record Link(double distance, int firstSign, int secondSign, Vec3 firstExit) {
    }

    private record Entry(BlockPos pos, Vec3i exit0, Vec3i exit1, int entered, double length) {
    }

    public static @Nullable Position locate(final AbstractMinecart cart) {
        final BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        final Entry rail = rail(cart.level(), pos);

        if (rail == null) {
            return null;
        }

        final double startX = pos.getX() + 0.5D + rail.exit0().getX() * 0.5D;
        final double startZ = pos.getZ() + 0.5D + rail.exit0().getZ() * 0.5D;
        final double dirX = (rail.exit1().getX() - rail.exit0().getX()) * 0.5D / rail.length();
        final double dirZ = (rail.exit1().getZ() - rail.exit0().getZ()) * 0.5D / rail.length();
        final double t = (cart.getX() - startX) * dirX + (cart.getZ() - startZ) * dirZ;

        return new Position(pos, rail.exit0(), rail.exit1(), rail.length(), Math.clamp(t, 0.0D, rail.length()));
    }

    public static @Nullable Link between(final Level level, final Position first, final Position second) {
        if (first.pos().equals(second.pos())) {
            final boolean ahead = second.t() >= first.t();
            return new Link(Math.abs(second.t() - first.t()),
                    ahead ? -1 : 1,
                    ahead ? 1 : -1,
                    direction(first.exit(ahead ? 1 : 0)));
        }

        final Link viaExit1 = walk(level, first, second, 1);
        final Link viaExit0 = walk(level, first, second, 0);

        if (viaExit1 == null) {
            return viaExit0;
        }

        if (viaExit0 == null) {
            return viaExit1;
        }

        return viaExit0.distance() < viaExit1.distance() ? viaExit0 : viaExit1;
    }

    private static @Nullable Link walk(final Level level, final Position first, final Position second,
                                       final int startExit) {
        double distance = startExit == 1 ? first.length() - first.t() : first.t();
        BlockPos pos = first.pos();
        Vec3i out = first.exit(startExit);

        for (int step = 0; step < MAX_BLOCKS; step++) {
            final Entry next = connected(level, pos, out);

            if (next == null) {
                return null;
            }

            if (next.pos().equals(second.pos())) {
                distance += next.entered() == 0 ? second.t() : second.length() - second.t();
                return new Link(distance,
                        startExit == 1 ? -1 : 1,
                        next.entered() == 1 ? -1 : 1,
                        direction(first.exit(startExit)));
            }

            distance += next.length();
            pos = next.pos();
            out = next.entered() == 0 ? next.exit1() : next.exit0();
        }

        return null;
    }

    private static @Nullable Entry connected(final Level level, final BlockPos from, final Vec3i out) {
        for (final int dy : VERTICAL_SEARCH) {
            final BlockPos candidate = from.offset(out.getX(), dy, out.getZ());
            final Entry rail = rail(level, candidate);

            if (rail == null) {
                continue;
            }

            if (opposes(rail.exit0(), out)) {
                return new Entry(candidate, rail.exit0(), rail.exit1(), 0, rail.length());
            }

            if (opposes(rail.exit1(), out)) {
                return new Entry(candidate, rail.exit0(), rail.exit1(), 1, rail.length());
            }
        }

        return null;
    }

    private static @Nullable Entry rail(final Level level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }

        final RailShape shape = state.getValue(rail.getShapeProperty());
        final Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        final Vec3i exit0 = new Vec3i(exits.getFirst().getX(), 0, exits.getFirst().getZ());
        final Vec3i exit1 = new Vec3i(exits.getSecond().getX(), 0, exits.getSecond().getZ());
        final double dx = (exit1.getX() - exit0.getX()) * 0.5D;
        final double dz = (exit1.getZ() - exit0.getZ()) * 0.5D;

        return new Entry(pos, exit0, exit1, -1, Math.sqrt(dx * dx + dz * dz));
    }

    private static boolean opposes(final Vec3i exit, final Vec3i out) {
        return exit.getX() == -out.getX() && exit.getZ() == -out.getZ();
    }

    private static Vec3 direction(final Vec3i exit) {
        return new Vec3(exit.getX(), 0.0D, exit.getZ());
    }
}
