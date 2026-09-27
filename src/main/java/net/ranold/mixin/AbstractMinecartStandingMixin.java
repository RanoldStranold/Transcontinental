package net.ranold.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.ranold.standing.MinecartStanding;
import net.ranold.standing.StandableMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartStandingMixin extends VehicleEntity implements StandableMinecart {

    @Unique
    private static final EntityDataAccessor<Boolean> TRANSCONTINENTAL$STANDABLE =
            SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);

    AbstractMinecartStandingMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Override
    public boolean transcontinental$standable() {
        return this.getEntityData().get(TRANSCONTINENTAL$STANDABLE);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void transcontinental$defineStandableData(final SynchedEntityData.Builder builder, final CallbackInfo ci) {
        builder.define(TRANSCONTINENTAL$STANDABLE, false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void transcontinental$updateStandable(final CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            this.getEntityData().set(TRANSCONTINENTAL$STANDABLE,
                    MinecartStanding.isStationary((AbstractMinecart) (Object) this));
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void transcontinental$noPushFromStandingPlayer(final Entity other, final CallbackInfo ci) {
        final AbstractMinecart self = (AbstractMinecart) (Object) this;

        if (other instanceof Player
                && this.transcontinental$standable()
                && MinecartStanding.isAboveFloor(self, other)) {
            ci.cancel();
        }
    }
}
