package net.ranold.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import net.ranold.registry.TCBlocks;

import java.util.List;

public class transcontinentalClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockColorRegistry.register(List.of(BlockTintSources.redstone()), TCBlocks.IRON_POWERED_RAIL);
    }
}
