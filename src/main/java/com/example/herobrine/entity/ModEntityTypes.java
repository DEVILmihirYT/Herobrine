package com.example.herobrine.entity;

import com.example.HerobrineMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
    public static final EntityType<HerobrineEntity> HEROBRINE = register(
            "herobrine",
            EntityType.Builder.<HerobrineEntity>of(HerobrineEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
    );

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void registerModEntityTypes() {
        FabricDefaultAttributeRegistry.register(HEROBRINE, HerobrineEntity.createAttributes());
        HerobrineMod.LOGGER.info("Registering Herobrine entity type");
    }

    private ModEntityTypes() {}
}
