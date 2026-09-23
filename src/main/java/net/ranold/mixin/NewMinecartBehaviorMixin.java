package net.ranold.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.ranold.rail.RailSpeeds;
import net.ranold.registry.TCBlocks;
import net.ranold.sound.MinecartCollisionSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {

    NewMinecartBehaviorMixin(final AbstractMinecart minecart) {
        super(minecart);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void transcontinental$collisionSound(final CallbackInfo ci) {
        MinecartCollisionSound.tick(this.minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void transcontinental$raiseSpeedCeiling(final ServerLevel level,
                                                    final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(this.minecart.isInWater()
                ? RailSpeeds.GOLD_MAX * RailSpeeds.WATER_FACTOR
                : RailSpeeds.GOLD_MAX);
    }

    @Inject(method = "getSlowdownFactor", at = @At("HEAD"), cancellable = true)
    private void transcontinental$suppressDragOnModRails(final CallbackInfoReturnable<Double> cir) {
        final BlockPos pos = this.minecart.getCurrentBlockPosOrRailBelow();
        final BlockState state = this.minecart.level().getBlockState(pos);

        if (RailSpeeds.holdsSpeed(state)) {
            cir.setReturnValue(1.0D);
        }
    }

    @Inject(method = "calculateHaltTrackSpeed", at = @At("HEAD"), cancellable = true)
    private void transcontinental$brakeOnUnpoweredModRails(final Vec3 speed,
                                                           final BlockState state,
                                                           final CallbackInfoReturnable<Vec3> cir) {
        if (!RailSpeeds.isModRail(state) || RailSpeeds.isPowered(state)) {
            return;
        }

        cir.setReturnValue(speed.length() < RailSpeeds.HALT_THRESHOLD
                ? Vec3.ZERO
                : speed.scale(RailSpeeds.HALT_FACTOR));
    }

    @Inject(method = "calculateBoostTrackSpeed", at = @At("HEAD"), cancellable = true)
    private void transcontinental$boost(final Vec3 speed,
                                        final BlockPos pos,
                                        final BlockState state,
                                        final CallbackInfoReturnable<Vec3> cir) {
        if (state.is(TCBlocks.OMEGA_RAIL)) {
            if (speed.length() > RailSpeeds.MOVING_THRESHOLD) {
                cir.setReturnValue(speed.normalize().scale(RailSpeeds.GOLD_MAX));
                return;
            }

            final Vec3 launchDirection = this.transcontinental$launchDirection(pos, state);

            cir.setReturnValue(launchDirection.lengthSqr() <= 0.0D
                    ? speed
                    : launchDirection.normalize().scale(RailSpeeds.GOLD_MAX));
            return;
        }

        final boolean gold = state.is(TCBlocks.GOLD_POWERED_RAIL);
        final boolean iron = state.is(TCBlocks.IRON_POWERED_RAIL);
        final boolean vanilla = state.is(Blocks.POWERED_RAIL);

        if (!gold && !iron && !vanilla) {
            return;
        }

        if (!RailSpeeds.isPowered(state)) {
            cir.setReturnValue(speed);
            return;
        }

        final double cap = gold
                ? RailSpeeds.GOLD_MAX
                : iron ? RailSpeeds.ironCap(state) : RailSpeeds.VANILLA_MAX;
        final double accel = gold ? RailSpeeds.GOLD_ACCEL : RailSpeeds.IRON_ACCEL;
        final double length = speed.length();

        if (length > RailSpeeds.MOVING_THRESHOLD) {
            if (length >= cap) {
                cir.setReturnValue(speed);
                return;
            }

            cir.setReturnValue(speed.normalize().scale(Math.min(cap, length + accel)));
            return;
        }

        final Vec3 redstoneDirection = this.transcontinental$launchDirection(pos, state);

        cir.setReturnValue(redstoneDirection.lengthSqr() <= 0.0D
                ? speed
                : redstoneDirection.normalize().scale(RailSpeeds.STANDSTILL_KICK));
    }

    @Unique
    private Vec3 transcontinental$launchDirection(final BlockPos pos, final BlockState state) {
        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return Vec3.ZERO;
        }

        final RailShape shape = state.getValue(rail.getShapeProperty());

        if (shape == RailShape.EAST_WEST) {
            if (this.minecart.isRedstoneConductor(pos.west())) {
                return new Vec3(1.0D, 0.0D, 0.0D);
            }

            if (this.minecart.isRedstoneConductor(pos.east())) {
                return new Vec3(-1.0D, 0.0D, 0.0D);
            }
        } else if (shape == RailShape.NORTH_SOUTH) {
            if (this.minecart.isRedstoneConductor(pos.north())) {
                return new Vec3(0.0D, 0.0D, 1.0D);
            }

            if (this.minecart.isRedstoneConductor(pos.south())) {
                return new Vec3(0.0D, 0.0D, -1.0D);
            }
        }

        return Vec3.ZERO;
    }
}
