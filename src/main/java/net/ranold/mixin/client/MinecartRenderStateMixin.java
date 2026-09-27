package net.ranold.mixin.client;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.ranold.client.coupling.ChainRenderState;
import net.ranold.client.coupling.ChainSegments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public abstract class MinecartRenderStateMixin implements ChainRenderState {

    @Unique
    private final ChainSegments transcontinental$chains = new ChainSegments();

    @Override
    public ChainSegments transcontinental$chains() {
        return this.transcontinental$chains;
    }
}
