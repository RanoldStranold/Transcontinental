package net.ranold.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.ranold.sound.CollisionSoundHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartCollisionMixin implements CollisionSoundHolder {

    @Unique
    private int transcontinental$lastCollisionTick = -1000;

    @Override
    public int transcontinental$lastCollisionTick() {
        return this.transcontinental$lastCollisionTick;
    }

    @Override
    public void transcontinental$setLastCollisionTick(final int tick) {
        this.transcontinental$lastCollisionTick = tick;
    }
}
