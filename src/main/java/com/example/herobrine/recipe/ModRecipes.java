package com.example.herobrine.recipe;

import com.example.HerobrineMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModRecipes {
    public static void register() {
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, "dark_frame_ritual"),
                DarkFrameRitualRecipe.SERIALIZER
        );
        HerobrineMod.LOGGER.info("Registered Dark Frame ritual recipe");
    }

    private ModRecipes() {
    }
}
