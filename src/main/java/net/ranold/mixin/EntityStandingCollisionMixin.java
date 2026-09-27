package net.ranold.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ranold.standing.MinecartStanding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityStandingCollisionMixin {

    @WrapOperation(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> transcontinental$standOnMinecarts(final Level level,
                                                               final Entity source,
                                                               final AABB area,
                                                               final Operation<List<VoxelShape>> original) {
        return MinecartStanding.withStandingCollisions(source, area, original.call(level, source, area));
    }
}
