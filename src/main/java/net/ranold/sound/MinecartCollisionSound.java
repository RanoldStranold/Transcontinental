package net.ranold.sound;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import net.ranold.rail.RailSpeeds;

import java.util.List;

public final class MinecartCollisionSound {

    private MinecartCollisionSound() {
    }

    private static final int COOLDOWN_TICKS = 10;

    private static final double SEARCH_INFLATE = 0.35D;

    private static final double MIN_IMPACT_SPEED = 0.02D;

    private static final float MIN_PITCH = 0.8F;

    private static final float MAX_PITCH = 1.0F;

    private static final float MIN_VOLUME = 0.0F;

    private static final float MAX_VOLUME = 1.2F;

    private static final double VOLUME_CURVE = 3.0D;

    public static void tick(final AbstractMinecart minecart) {
        if (!(minecart.level() instanceof ServerLevel level)) {
            return;
        }

        final List<Entity> nearby = level.getEntities(minecart,
                minecart.getBoundingBox().inflate(SEARCH_INFLATE),
                entity -> entity instanceof AbstractMinecart);

        if (nearby.isEmpty()) {
            return;
        }

        for (final Entity entity : nearby) {
            final AbstractMinecart other = (AbstractMinecart) entity;

            if (minecart.getId() > other.getId()) {
                continue;
            }

            tryPlay(level, minecart, other);
        }
    }

    private static void tryPlay(final ServerLevel level,
                                final AbstractMinecart first,
                                final AbstractMinecart second) {
        final Vec3 closing = first.getDeltaMovement().subtract(second.getDeltaMovement());
        final double impactSpeed = closing.length();

        if (impactSpeed < MIN_IMPACT_SPEED) {
            return;
        }

        final Vec3 separation = second.position().subtract(first.position());

        if (separation.lengthSqr() > 1.0E-6D && closing.dot(separation) <= 0.0D) {
            return;
        }

        if (!claim(first) || !claim(second)) {
            return;
        }

        final float strength = (float) Mth.clamp(impactSpeed / RailSpeeds.GOLD_MAX, 0.0D, 1.0D);
        final float loudness = (float) Math.pow(strength, VOLUME_CURVE);
        final float pitch = Mth.lerp(strength, MAX_PITCH, MIN_PITCH);
        final float volume = Mth.lerp(loudness, MIN_VOLUME, MAX_VOLUME);

        if (volume <= 0.0F) {
            return;
        }

        level.playSound(null,
                (first.getX() + second.getX()) * 0.5D,
                (first.getY() + second.getY()) * 0.5D,
                (first.getZ() + second.getZ()) * 0.5D,
                SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, volume, pitch);
    }

    private static boolean claim(final AbstractMinecart minecart) {
        final CollisionSoundHolder holder = (CollisionSoundHolder) minecart;
        final int last = holder.transcontinental$lastCollisionTick();

        if (last >= 0 && minecart.tickCount - last < COOLDOWN_TICKS) {
            return false;
        }

        holder.transcontinental$setLastCollisionTick(minecart.tickCount);
        return true;
    }
}
