package com.example.herobrine.item;

import com.example.HerobrineMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item MASHAAL = register(
            "mashaal",
            MashaalItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static void registerModItems() {
        CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.accept(MASHAAL));

        HerobrineMod.LOGGER.info("Registering Herobrine Mashaal");
    }

    private static <T extends Item> T register(
            String name,
            Function<Item.Properties, T> itemFactory,
            Item.Properties properties
    ) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, name)
        );

        T item = itemFactory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private ModItems() {
    }
}
