package net.ranold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.ranold.coupling.CouplingInteraction;
import net.ranold.debug.CartLogger;
import net.ranold.registry.TCBlocks;

public class transcontinental implements ModInitializer {

    public static final String MOD_ID = "transcontinental";

    @Override
    public void onInitialize() {
        TCBlocks.init();
        UseEntityCallback.EVENT.register(CouplingInteraction::onUseEntity);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CartLogger.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(CartLogger::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CartLogger.close());
    }
}
