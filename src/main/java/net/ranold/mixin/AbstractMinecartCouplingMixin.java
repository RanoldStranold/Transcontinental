package net.ranold.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ranold.coupling.CoupledMinecart;
import net.ranold.coupling.MinecartCoupling;
import net.ranold.coupling.MinecartLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartCouplingMixin extends VehicleEntity implements CoupledMinecart {

    @Unique
    private static final String TRANSCONTINENTAL$LINKS_KEY = "TranscontinentalLinks";

    @Unique
    private static final EntityDataAccessor<Integer> TRANSCONTINENTAL$PARTNER_A =
            SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);

    @Unique
    private static final EntityDataAccessor<Integer> TRANSCONTINENTAL$PARTNER_B =
            SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);

    @Unique
    private static final String TRANSCONTINENTAL$ENDS_KEY = "TranscontinentalLinkEnds";

    @Unique
    private static final EntityDataAccessor<Byte> TRANSCONTINENTAL$ENDS =
            SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BYTE);

    @Unique
    private final MinecartLinks transcontinental$links = new MinecartLinks();

    AbstractMinecartCouplingMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Override
    public MinecartLinks transcontinental$links() {
        return this.transcontinental$links;
    }

    @Override
    public int transcontinental$syncedPartner(final int slot) {
        return this.getEntityData().get(slot == 0 ? TRANSCONTINENTAL$PARTNER_A : TRANSCONTINENTAL$PARTNER_B);
    }

    @Override
    public void transcontinental$setSyncedPartner(final int slot, final int entityId) {
        this.getEntityData().set(slot == 0 ? TRANSCONTINENTAL$PARTNER_A : TRANSCONTINENTAL$PARTNER_B, entityId);
    }

    @Override
    public int transcontinental$syncedEnd(final int slot) {
        final int code = (this.getEntityData().get(TRANSCONTINENTAL$ENDS) >> (slot * 2)) & 3;
        return code == 1 ? 1 : code == 2 ? -1 : 0;
    }

    @Override
    public void transcontinental$setSyncedEnd(final int slot, final int end) {
        final int shift = slot * 2;
        final int code = end > 0 ? 1 : end < 0 ? 2 : 0;
        final int packed = (this.getEntityData().get(TRANSCONTINENTAL$ENDS) & ~(3 << shift)) | (code << shift);
        this.getEntityData().set(TRANSCONTINENTAL$ENDS, (byte) packed);
    }

    @Override
    public void onRemoval(final Entity.RemovalReason reason) {
        super.onRemoval(reason);

        if (reason.shouldDestroy() && !this.level().isClientSide()) {
            MinecartCoupling.unlinkAll((AbstractMinecart) (Object) this, reason == Entity.RemovalReason.KILLED);
        }
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void transcontinental$defineCouplingData(final SynchedEntityData.Builder builder, final CallbackInfo ci) {
        builder.define(TRANSCONTINENTAL$PARTNER_A, MinecartCoupling.NO_PARTNER);
        builder.define(TRANSCONTINENTAL$PARTNER_B, MinecartCoupling.NO_PARTNER);
        builder.define(TRANSCONTINENTAL$ENDS, (byte) 0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void transcontinental$saveLinks(final ValueOutput output, final CallbackInfo ci) {
        final List<UUID> partners = new ArrayList<>(MinecartLinks.SLOTS);
        final List<Integer> ends = new ArrayList<>(MinecartLinks.SLOTS);

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final UUID partner = this.transcontinental$links.partner(slot);

            if (partner != null) {
                partners.add(partner);
                ends.add(this.transcontinental$links.end(slot));
            }
        }

        if (!partners.isEmpty()) {
            output.store(TRANSCONTINENTAL$LINKS_KEY, UUIDUtil.CODEC.listOf(), partners);
            output.store(TRANSCONTINENTAL$ENDS_KEY, Codec.INT.listOf(), ends);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void transcontinental$loadLinks(final ValueInput input, final CallbackInfo ci) {
        final List<UUID> partners = input.read(TRANSCONTINENTAL$LINKS_KEY, UUIDUtil.CODEC.listOf()).orElse(List.of());
        final List<Integer> ends = input.read(TRANSCONTINENTAL$ENDS_KEY, Codec.INT.listOf()).orElse(List.of());

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            this.transcontinental$links.setPartner(slot, slot < partners.size() ? partners.get(slot) : null);
            this.transcontinental$links.setEnd(slot, slot < ends.size() ? ends.get(slot) : 0);
        }
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void transcontinental$passThroughCoupled(final Entity other, final CallbackInfoReturnable<Boolean> cir) {
        if (MinecartCoupling.isLinked((AbstractMinecart) (Object) this, other)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void transcontinental$noPushFromCoupled(final Entity other, final CallbackInfo ci) {
        if (MinecartCoupling.isLinked((AbstractMinecart) (Object) this, other)) {
            ci.cancel();
        }
    }
}
