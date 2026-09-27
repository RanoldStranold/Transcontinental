package net.ranold.coupling;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ranold.coupling.MinecartCoupling.Separation;
import net.ranold.coupling.MinecartCoupling.TrackAxis;
import net.ranold.registry.TCBlocks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class TrainSolver {

    private TrainSolver() {
    }

    private static final double DRIVEN_INVERSE_MASS = 0.001D;

    private static final double BIAS = 0.3D;

    private static final double MAX_BIAS = 0.1D;

    private static final double INACTIVE_RATIO = 0.1D;

    private static final double COMPLIANCE = 1.0E-6D;

    private static final int FREE = 0;

    private static final int TAUT = 1;

    private static final int CONTACT = 2;

    private static final int RIGID = 3;

    private static final int ACTIVE_SET_PASSES = 4;

    private static final double BOUND_TOLERANCE = 0.01D;

    public static void tick(final AbstractMinecart cart) {
        if (!(cart.level() instanceof ServerLevel level)) {
            return;
        }

        final MinecartLinks links = MinecartCoupling.links(cart);
        final long now = level.getGameTime();

        if (links.isEmpty() || links.solvedTick() == now) {
            return;
        }

        List<AbstractMinecart> train = MinecartCoupling.train(cart);

        if (maintain(level, train)) {
            train = MinecartCoupling.train(cart);
        }

        for (final AbstractMinecart member : train) {
            MinecartCoupling.links(member).setSolvedTick(now);
        }

        if (train.size() > 1) {
            solve(level, train);
        }
    }

    private static boolean maintain(final ServerLevel level, final List<AbstractMinecart> train) {
        boolean changed = false;

        for (final AbstractMinecart member : train) {
            final MinecartLinks links = MinecartCoupling.links(member);
            links.tickHold();

            for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
                final UUID id = links.partner(slot);

                if (id == null) {
                    continue;
                }

                final AbstractMinecart partner = MinecartCoupling.resolve(member, slot);

                if (partner != null) {
                    links.clearMissing(slot);

                    if (links.end(slot) == 0) {
                        MinecartCoupling.assignEnd(member, slot, partner);
                    }
                } else if (MinecartCoupling.findAnywhere(level, id) != null) {
                    links.clearMissing(slot);
                } else if (links.tickMissing(slot) > MinecartCoupling.PRUNE_TICKS) {
                    links.setPartner(slot, null);
                    changed = true;
                }
            }

            if (links.partner(0) != null && links.partner(1) != null
                    && links.end(0) != 0 && links.end(0) == links.end(1)) {
                final AbstractMinecart duplicate = MinecartCoupling.resolve(member, 1);

                if (duplicate != null) {
                    MinecartCoupling.breakLink(level, member, duplicate);
                    changed = true;
                }
            }

            MinecartCoupling.syncPartners(member);
        }

        for (int i = 0; i + 1 < train.size(); i++) {
            final AbstractMinecart first = train.get(i);
            final AbstractMinecart second = train.get(i + 1);

            if (isTicking(level, first) && isTicking(level, second)
                    && MinecartCoupling.gap(first, second) > MinecartCoupling.BREAK_GAP) {
                MinecartCoupling.breakLink(level, first, second);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isTicking(final ServerLevel level, final AbstractMinecart cart) {
        return level.isPositionEntityTicking(cart.blockPosition());
    }

    private static boolean isDriven(final AbstractMinecart cart, final @Nullable TrackAxis axis) {
        if (cart instanceof MinecartFurnace furnace && furnace.push.lengthSqr() > 1.0E-7D) {
            return true;
        }

        if (axis == null) {
            return false;
        }

        final BlockState state = axis.state();
        return state.is(Blocks.POWERED_RAIL)
                || state.is(TCBlocks.IRON_POWERED_RAIL)
                || state.is(TCBlocks.GOLD_POWERED_RAIL)
                || state.is(TCBlocks.OMEGA_RAIL);
    }

    private static void release(final AbstractMinecart cart, final AbstractMinecart partner) {
        final MinecartLinks links = MinecartCoupling.links(cart);
        final int slot = links.slotOf(partner.getUUID());

        if (slot >= 0) {
            links.release(slot);
        }
    }

    private static Vec3 railJacobian(final double value) {
        return new Vec3(value, 0.0D, 0.0D);
    }

    private static void solve(final ServerLevel level, final List<AbstractMinecart> train) {
        final int count = train.size();
        final int constraints = count - 1;

        final TrackAxis[] axes = new TrackAxis[count];
        final double[] inverseMass = new double[count];
        final Vec3[] velocity = new Vec3[count];

        for (int i = 0; i < count; i++) {
            final AbstractMinecart cart = train.get(i);
            final MinecartLinks links = MinecartCoupling.links(cart);
            final TrackAxis axis = MinecartCoupling.trackAxis(cart);
            final boolean ticking = isTicking(level, cart);

            if (ticking) {
                removePendingBias(cart, links, axis);
            }

            final Vec3 delta = cart.getDeltaMovement();
            axes[i] = axis;
            inverseMass[i] = !ticking || links.isHolding()
                    ? 0.0D
                    : isDriven(cart, axis) ? DRIVEN_INVERSE_MASS : 1.0D;
            velocity[i] = axis == null ? delta : railJacobian(delta.horizontal().dot(axis.horizontal()));
        }

        final Vec3[] firstJacobian = new Vec3[constraints];
        final Vec3[] secondJacobian = new Vec3[constraints];
        final double[] gap = new double[constraints];
        final double[] minimum = new double[constraints];
        final double[] diagonal = new double[constraints];
        final double[] coupling = new double[constraints];
        final boolean[] usable = new boolean[constraints];

        for (int k = 0; k < constraints; k++) {
            final AbstractMinecart first = train.get(k);
            final AbstractMinecart second = train.get(k + 1);
            final Separation separation = axes[k] != null && axes[k + 1] != null
                    ? MinecartCoupling.separation(first, second)
                    : new Separation(first.position().distanceTo(second.position()), null);

            if (separation.path() != null) {
                firstJacobian[k] = railJacobian(separation.path().firstSign());
                secondJacobian[k] = railJacobian(separation.path().secondSign());
            } else {
                final Vec3 centres = second.position().subtract(first.position());
                final Vec3 normal = centres.lengthSqr() > 1.0E-12D ? centres.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
                firstJacobian[k] = axes[k] == null ? normal.scale(-1.0D) : railJacobian(-normal.dot(axes[k].full()));
                secondJacobian[k] = axes[k + 1] == null ? normal : railJacobian(normal.dot(axes[k + 1].full()));
            }

            gap[k] = separation.distance() - 2.0D * MinecartCoupling.HALF_LENGTH;
            minimum[k] = minimumGap(speedOf(velocity[k], axes[k]), speedOf(velocity[k + 1], axes[k + 1]));

            if (gap[k] - MinecartCoupling.GAP < MinecartCoupling.SETTLED_ERROR) {
                release(first, second);
                release(second, first);
            }

            final double raw = inverseMass[k] * firstJacobian[k].lengthSqr()
                    + inverseMass[k + 1] * secondJacobian[k].lengthSqr();
            final double reference = inverseMass[k] + inverseMass[k + 1];

            usable[k] = reference > 0.0D && raw > INACTIVE_RATIO * reference;
            diagonal[k] = raw + COMPLIANCE;
        }

        for (int k = 0; k + 1 < constraints; k++) {
            coupling[k] = inverseMass[k + 1] * secondJacobian[k].dot(firstJacobian[k + 1]);
        }

        final int[] mode = new int[constraints];
        final double[] rate = new double[constraints];

        for (int k = 0; k < constraints; k++) {
            rate[k] = firstJacobian[k].dot(velocity[k]) + secondJacobian[k].dot(velocity[k + 1]);
            mode[k] = !usable[k] ? FREE : velocityMode(gap[k], minimum[k], rate[k]);
        }

        final double[] target = new double[constraints];

        for (int k = 0; k < constraints; k++) {
            target[k] = -rate[k];
        }

        double[] impulse = solveActive(mode, diagonal, coupling, target);

        for (int pass = 0; pass < ACTIVE_SET_PASSES; pass++) {
            boolean dropped = false;

            for (int k = 0; k < constraints; k++) {
                if (mode[k] == TAUT && impulse[k] > 0.0D || mode[k] == CONTACT && impulse[k] < 0.0D) {
                    mode[k] = FREE;
                    dropped = true;
                }
            }

            if (!dropped) {
                break;
            }

            impulse = solveActive(mode, diagonal, coupling, target);
        }

        final Vec3[] solved = apply(velocity, inverseMass, firstJacobian, secondJacobian, mode, impulse);

        final int[] biasMode = new int[constraints];

        for (int k = 0; k < constraints; k++) {
            final double excess = gap[k] - MinecartCoupling.GAP;
            final double shortfall = minimum[k] - gap[k];

            if (!usable[k]) {
                biasMode[k] = FREE;
            } else if (excess > 0.0D) {
                biasMode[k] = RIGID;
                target[k] = -Math.min(BIAS * excess, MAX_BIAS);
            } else if (shortfall > 0.0D) {
                biasMode[k] = RIGID;
                target[k] = Math.min(BIAS * shortfall, MAX_BIAS);
            } else {
                biasMode[k] = FREE;
            }
        }

        final double[] biasImpulse = solveActive(biasMode, diagonal, coupling, target);
        final Vec3[] zero = new Vec3[count];

        for (int i = 0; i < count; i++) {
            zero[i] = Vec3.ZERO;
        }

        final Vec3[] bias = apply(zero, inverseMass, firstJacobian, secondJacobian, biasMode, biasImpulse);

        for (int i = 0; i < count; i++) {
            if (inverseMass[i] == 0.0D) {
                continue;
            }

            final AbstractMinecart cart = train.get(i);
            final TrackAxis axis = axes[i];
            final Vec3 total = solved[i].add(bias[i]);
            final Vec3 worldBias;

            if (axis == null) {
                cart.setDeltaMovement(total);
                worldBias = bias[i];
            } else {
                cart.setDeltaMovement(axis.horizontal().x * total.x,
                        cart.getDeltaMovement().y,
                        axis.horizontal().z * total.x);
                worldBias = axis.horizontal().scale(bias[i].x);
            }

            MinecartCoupling.links(cart).setPendingBias(worldBias);
        }
    }

    private static int velocityMode(final double gap, final double minimum, final double rate) {
        if (MinecartCoupling.GAP - minimum < BOUND_TOLERANCE) {
            return gap >= MinecartCoupling.GAP - BOUND_TOLERANCE || gap <= minimum + BOUND_TOLERANCE ? RIGID : FREE;
        }

        if (gap >= MinecartCoupling.GAP - BOUND_TOLERANCE && rate > 0.0D) {
            return TAUT;
        }

        if (gap <= minimum + BOUND_TOLERANCE && rate < 0.0D) {
            return CONTACT;
        }

        return FREE;
    }

    private static double speedOf(final Vec3 velocity, final @Nullable TrackAxis axis) {
        return axis == null ? velocity.horizontalDistance() : Math.abs(velocity.x);
    }

    private static double minimumGap(final double firstSpeed, final double secondSpeed) {
        final double speed = Math.max(firstSpeed, secondSpeed);
        final double stiffness = Mth.clamp((speed - MinecartCoupling.SLACK_FULL_SPEED)
                / (MinecartCoupling.RIGID_SPEED - MinecartCoupling.SLACK_FULL_SPEED), 0.0D, 1.0D);

        return Mth.lerp(stiffness, MinecartCoupling.MIN_GAP, MinecartCoupling.GAP);
    }

    private static void removePendingBias(final AbstractMinecart cart, final MinecartLinks links,
                                          final @Nullable TrackAxis axis) {
        final Vec3 bias = links.pendingBias();

        if (bias.lengthSqr() == 0.0D) {
            return;
        }

        links.setPendingBias(Vec3.ZERO);
        final Vec3 delta = cart.getDeltaMovement();

        if (axis == null) {
            final Vec3 corrected = delta.subtract(bias);
            cart.setDeltaMovement(corrected.dot(delta) < 0.0D ? new Vec3(0.0D, delta.y, 0.0D) : corrected);
            return;
        }

        final double speed = delta.horizontal().dot(axis.horizontal());
        final double corrected = speed - bias.dot(axis.horizontal());
        final double kept = corrected * speed < 0.0D ? 0.0D : corrected;

        cart.setDeltaMovement(axis.horizontal().x * kept, delta.y, axis.horizontal().z * kept);
    }

    private static Vec3[] apply(final Vec3[] velocity, final double[] inverseMass, final Vec3[] firstJacobian,
                                final Vec3[] secondJacobian, final int[] mode, final double[] impulse) {
        final int count = velocity.length;
        final Vec3[] result = new Vec3[count];

        for (int i = 0; i < count; i++) {
            Vec3 change = Vec3.ZERO;

            if (i > 0 && mode[i - 1] != FREE) {
                change = change.add(secondJacobian[i - 1].scale(impulse[i - 1]));
            }

            if (i < count - 1 && mode[i] != FREE) {
                change = change.add(firstJacobian[i].scale(impulse[i]));
            }

            result[i] = velocity[i].add(change.scale(inverseMass[i]));
        }

        return result;
    }

    private static double[] solveActive(final int[] mode, final double[] diagonal, final double[] coupling,
                                        final double[] target) {
        final int size = mode.length;
        final double[] activeDiagonal = new double[size];
        final double[] activeCoupling = new double[size];
        final double[] activeTarget = new double[size];

        for (int k = 0; k < size; k++) {
            final boolean active = mode[k] != FREE;
            activeDiagonal[k] = active ? diagonal[k] : 1.0D;
            activeTarget[k] = active ? target[k] : 0.0D;
            activeCoupling[k] = active && k + 1 < size && mode[k + 1] != FREE ? coupling[k] : 0.0D;
        }

        return solveTridiagonal(activeDiagonal, activeCoupling, activeTarget);
    }

    private static double[] solveTridiagonal(final double[] diagonal, final double[] offDiagonal, final double[] rhs) {
        final int size = diagonal.length;
        final double[] upper = new double[size];
        final double[] result = new double[size];

        double pivot = diagonal[0];
        upper[0] = size > 1 ? offDiagonal[0] / pivot : 0.0D;
        result[0] = rhs[0] / pivot;

        for (int k = 1; k < size; k++) {
            pivot = diagonal[k] - offDiagonal[k - 1] * upper[k - 1];
            upper[k] = k + 1 < size ? offDiagonal[k] / pivot : 0.0D;
            result[k] = (rhs[k] - offDiagonal[k - 1] * result[k - 1]) / pivot;
        }

        for (int k = size - 2; k >= 0; k--) {
            result[k] -= upper[k] * result[k + 1];
        }

        return result;
    }
}
