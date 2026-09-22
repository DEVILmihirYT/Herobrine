package com.example.herobrine.block;

import com.example.HerobrineMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;

public final class ModBlocks {
    public static final Block DARK_FRAME = register(
            "dark_frame",
            DarkFrameBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(50.0F, 1200.0F)
    );

    public static final Block DARK_PORTAL = register(
            "dark_portal",
            DarkPortalBlock::new,
            BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(-1.0F).lightLevel(state -> 7)
    );

    public static void registerModBlocks() {
        CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS)
                .register(output -> output.accept(DARK_FRAME.asItem()));
        HerobrineMod.LOGGER.info("Registering Dark Frame and Dark Portal");
    }

    private static <T extends Block> T register(
            String name,
            Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties
    ) {
        ResourceKey<Block> blockKey = ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, name)
        );
        T block = factory.apply(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, name)
        );
        BlockItem item = new BlockItem(
                block,
                new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)
        );
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return block;
    }

    private ModBlocks() {
    }
}
