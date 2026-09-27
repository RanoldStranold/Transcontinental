package net.ranold.coupling;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MinecartCoupling {

    private MinecartCoupling() {
    }

    public static final double HALF_LENGTH = 0.625D;

    public static final double GAP = 1.0D;

    public static final double BREAK_GAP = 4.0D;

    public static final double LINK_GAP = 1.8D;

    public static final double MIN_GAP = 3.0D / 16.0D;

    public static final double SLACK_FULL_SPEED = 0.1D;

    public static final double RIGID_SPEED = 0.5D;

    public static final int HOLD_TICKS = 60;

    public static final double SETTLED_ERROR = 0.05D;

    public static final int MAX_TRAIN = 64;

    public static final int PRUNE_TICKS = 100;

    public static final int NO_PARTNER = -1;

    public record TrackAxis(Vec3 horizontal, Vec3 full, BlockState state) {
    }

    public record Separation(double distance, RailPath.@Nullable Link path) {
    }

    public static MinecartLinks links(final AbstractMinecart cart) {
        return ((CoupledMinecart) cart).transcontinental$links();
    }

    public static boolean isLinked(final AbstractMinecart cart, final Entity other) {
        return other instanceof AbstractMinecart && links(cart).slotOf(other.getUUID()) >= 0;
    }

    public static @Nullable AbstractMinecart resolve(final AbstractMinecart cart, final int slot) {
        final UUID id = links(cart).partner(slot);

        if (id == null) {
            return null;
        }

        return cart.level().getEntity(id) instanceof AbstractMinecart partner && !partner.isRemoved()
                ? partner
                : null;
    }

    public static @Nullable Entity findAnywhere(final ServerLevel level, final UUID id) {
        final Entity local = level.getEntity(id);

        if (local != null) {
            return local;
        }

        for (final ServerLevel other : level.getServer().getAllLevels()) {
            if (other != level) {
                final Entity found = other.getEntity(id);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public static List<AbstractMinecart> train(final AbstractMinecart start) {
        AbstractMinecart previous = null;
        AbstractMinecart current = start;

        for (int i = 0; i < MAX_TRAIN; i++) {
            final AbstractMinecart next = nextAlong(current, previous);

            if (next == null || next == start) {
                break;
            }

            previous = current;
            current = next;
        }

        final List<AbstractMinecart> train = new ArrayList<>();
        previous = null;

        while (current != null && train.size() < MAX_TRAIN && !train.contains(current)) {
            train.add(current);
            final AbstractMinecart next = nextAlong(current, previous);
            previous = current;
            current = next;
        }

        return train;
    }

    private static @Nullable AbstractMinecart nextAlong(final AbstractMinecart cart,
                                                       final @Nullable AbstractMinecart exclude) {
        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final AbstractMinecart partner = resolve(cart, slot);

            if (partner != null && partner != exclude) {
                return partner;
            }
        }

        return null;
    }

    public static boolean canLink(final AbstractMinecart first, final AbstractMinecart second) {
        return first != second
                && first.level() == second.level()
                && links(first).freeSlot() >= 0
                && links(second).freeSlot() >= 0
                && !train(first).contains(second);
    }

    public static void link(final AbstractMinecart first, final AbstractMinecart second) {
        final int firstSlot = links(first).freeSlot();
        final int secondSlot = links(second).freeSlot();
        links(first).setPartner(firstSlot, second.getUUID());
        links(second).setPartner(secondSlot, first.getUUID());
        assignEnd(first, firstSlot, second);
        assignEnd(second, secondSlot, first);
        links(second).hold(secondSlot, HOLD_TICKS);
        syncPartners(first);
        syncPartners(second);
    }

    public static void unlink(final AbstractMinecart first, final AbstractMinecart second) {
        clearPartner(first, second.getUUID());
        clearPartner(second, first.getUUID());
    }

    public static void unlinkAll(final AbstractMinecart cart, final boolean dropChains) {
        if (!(cart.level() instanceof ServerLevel level)) {
            return;
        }

        final MinecartLinks links = links(cart);

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final UUID id = links.partner(slot);

            if (id == null) {
                continue;
            }

            final Entity partner = findAnywhere(level, id);
            Vec3 dropAt = cart.position();

            if (partner instanceof AbstractMinecart partnerCart) {
                clearPartner(partnerCart, cart.getUUID());

                if (partnerCart.level() == level) {
                    dropAt = dropAt.add(partnerCart.position()).scale(0.5D);
                }
            }

            links.setPartner(slot, null);

            if (dropChains) {
                dropChain(level, dropAt);
            }
        }

        syncPartners(cart);
    }

    private static void clearPartner(final AbstractMinecart cart, final UUID partner) {
        final MinecartLinks links = links(cart);
        final int slot = links.slotOf(partner);

        if (slot >= 0) {
            links.setPartner(slot, null);
            syncPartners(cart);
        }
    }

    public static void breakLink(final ServerLevel level, final AbstractMinecart first, final AbstractMinecart second) {
        unlink(first, second);

        final Vec3 middle = first.position().add(second.position()).scale(0.5D);
        dropChain(level, middle);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    public static void dropChain(final ServerLevel level, final Vec3 position) {
        final ItemEntity item = new ItemEntity(level, position.x, position.y + 0.25D, position.z,
                new ItemStack(Items.IRON_CHAIN));
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    public static void syncPartners(final AbstractMinecart cart) {
        final CoupledMinecart coupled = (CoupledMinecart) cart;
        final MinecartLinks links = links(cart);

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final AbstractMinecart partner = resolve(cart, slot);
            coupled.transcontinental$setSyncedPartner(slot, partner == null ? NO_PARTNER : partner.getId());
            coupled.transcontinental$setSyncedEnd(slot, partner == null ? 0 : links.end(slot));
        }
    }

    public static void assignEnd(final AbstractMinecart cart, final int slot, final AbstractMinecart partner) {
        links(cart).setEnd(slot, facingEnd(cart, partner));
    }

    public static int facingEnd(final AbstractMinecart cart, final AbstractMinecart partner) {
        final Separation separation = separation(cart, partner);
        final Vec3 direction = separation.path() != null
                ? separation.path().firstExit()
                : partner.position().subtract(cart.position());

        return modelAxis(cart.getYRot(), cart.getXRot()).dot(direction) < 0.0D ? -1 : 1;
    }

    public static boolean isEndFree(final AbstractMinecart cart, final int end) {
        final MinecartLinks links = links(cart);

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            if (links.partner(slot) != null && links.end(slot) == end) {
                return false;
            }
        }

        return true;
    }

    public static Vec3 modelAxis(final float yRotDegrees, final float xRotDegrees) {
        final double yRot = Math.toRadians(yRotDegrees);
        final double xRot = Math.toRadians(xRotDegrees);
        final double cosX = Math.cos(xRot);
        return new Vec3(cosX * Math.cos(yRot), -Math.sin(xRot), -cosX * Math.sin(yRot));
    }

    public static @Nullable TrackAxis trackAxis(final AbstractMinecart cart) {
        final BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        final BlockState state = cart.level().getBlockState(pos);

        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }

        final RailShape shape = state.getValue(rail.getShapeProperty());
        final Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        final Vec3i from = exits.getFirst();
        final Vec3i to = exits.getSecond();
        final Vec3 horizontal = new Vec3(to.getX() - from.getX(), 0.0D, to.getZ() - from.getZ()).normalize();
        final Vec3 full = new Vec3(horizontal.x, Integer.signum(to.getY() - from.getY()), horizontal.z);

        return new TrackAxis(horizontal, full, state);
    }

    public static Separation separation(final AbstractMinecart first, final AbstractMinecart second) {
        final RailPath.Position firstRail = RailPath.locate(first);
        final RailPath.Position secondRail = firstRail == null ? null : RailPath.locate(second);

        if (firstRail != null && secondRail != null) {
            final RailPath.Link path = RailPath.between(first.level(), firstRail, secondRail);

            if (path != null) {
                return new Separation(path.distance(), path);
            }
        }

        return new Separation(first.position().distanceTo(second.position()), null);
    }

    public static double gap(final AbstractMinecart first, final AbstractMinecart second) {
        return separation(first, second).distance() - 2.0D * HALF_LENGTH;
    }
}
