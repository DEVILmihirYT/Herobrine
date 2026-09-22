package com.example.herobrine.block;

import com.example.HerobrineMod;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    /**
     * Dark Frame is a world-generation/endgame block only.
     *
     * It deliberately has no BlockItem, no recipe and no creative-tab entry.
     * The future castle schematic will place the block directly into the world.
     */
    public static final Block DARK_FRAME = registerBlockOnly(
            "dark_frame",
            DarkFrameBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(50.0F, 1200.0F)
    );

    /**
     * The portal is also a world-generated/event block only.
     * It has no inventory item.
     */
    public static final Block DARK_PORTAL = registerBlockOnly(
            "dark_portal",
            DarkPortalBlock::new,
            BlockBehaviour.Properties.of().noCollision().noOcclusion()
                    .strength(-1.0F)
                    .lightLevel(state -> 7)
    );

    public static void registerModBlocks() {
        HerobrineMod.LOGGER.info(
                "Registering Dark Frame and Dark Portal as world-only blocks"
        );
    }

    private static <T extends Block> T registerBlockOnly(
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
        return block;
    }

    private ModBlocks() {
    }
}
