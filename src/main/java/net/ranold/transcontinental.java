package net.ranold;

import net.fabricmc.api.ModInitializer;
import net.ranold.registry.TCBlocks;

public class transcontinental implements ModInitializer {

    public static final String MOD_ID = "transcontinental";

    @Override
    public void onInitialize() {
        TCBlocks.init();
    }
}
