package net.ranold.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.ranold.block.GoldPoweredRailBlock;
import net.ranold.block.IronPoweredRailBlock;
import net.ranold.block.OmegaRailBlock;
import net.ranold.block.PlainRailBlock;
import net.ranold.transcontinental;

import java.util.function.Function;

public final class TCBlocks {

    private TCBlocks() {
    }

    private static final ResourceKey<CreativeModeTab> REDSTONE_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "redstone_blocks"));

    private static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "tools_and_utilities"));

    public static final Block COPPER_RAIL =
            register("copper_rail", Blocks.RAIL, PlainRailBlock::new);

    public static final Block IRON_RAIL =
            register("iron_rail", Blocks.RAIL, PlainRailBlock::new);

    public static final Block GOLD_RAIL =
            register("gold_rail", Blocks.RAIL, PlainRailBlock::new);

    public static final Block IRON_POWERED_RAIL =
            register("iron_powered_rail", Blocks.POWERED_RAIL, IronPoweredRailBlock::new);

    public static final Block GOLD_POWERED_RAIL =
            register("gold_powered_rail", Blocks.POWERED_RAIL, GoldPoweredRailBlock::new);

    public static final Block OMEGA_RAIL =
            register("omega_rail", Blocks.POWERED_RAIL, OmegaRailBlock::new);

    private static Block register(final String name,
                                  final Block copyFrom,
                                  final Function<BlockBehaviour.Properties, Block> factory) {
        final Identifier id = Identifier.fromNamespaceAndPath(transcontinental.MOD_ID, name);
        final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        final Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                factory.apply(BlockBehaviour.Properties.ofFullCopy(copyFrom).setId(blockKey)));

        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));

        return block;
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(REDSTONE_TAB).register(TCBlocks::replaceVanillaRails);
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(TCBlocks::replaceVanillaRails);
    }

    private static void replaceVanillaRails(final FabricCreativeModeTabOutput output) {
        output.insertAfter(Items.RAIL,
                COPPER_RAIL.asItem(), IRON_RAIL.asItem(), GOLD_RAIL.asItem());
        output.insertAfter(Items.POWERED_RAIL,
                IRON_POWERED_RAIL.asItem(), GOLD_POWERED_RAIL.asItem(), OMEGA_RAIL.asItem());
        output.getDisplayStacks().removeIf(TCBlocks::isReplacedVanillaRail);
        output.getSearchTabStacks().removeIf(TCBlocks::isReplacedVanillaRail);
    }

    private static boolean isReplacedVanillaRail(final ItemStack stack) {
        return stack.is(Items.RAIL) || stack.is(Items.POWERED_RAIL);
    }
}
