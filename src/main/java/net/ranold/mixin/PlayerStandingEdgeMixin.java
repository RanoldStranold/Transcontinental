package net.ranold.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.ranold.standing.MinecartStanding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerStandingEdgeMixin {

    @WrapOperation(method = "canFallAtLeast",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean transcontinental$seeMinecartGround(final Level level,
                                                      final Entity source,
                                                      final AABB area,
                                                      final Operation<Boolean> original) {
        return original.call(level, source, area) && MinecartStanding.noStandingCollision(source, area);
    }
}
