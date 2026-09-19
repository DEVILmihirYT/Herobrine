package com.example;

import com.example.herobrine.HerobrineDeathManager;
import com.example.herobrine.HerobrineManager;
import com.example.herobrine.item.ModItems;
import com.example.herobrine.entity.ModEntityTypes;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HerobrineMod implements ModInitializer {
    public static final String MOD_ID = "herobrineai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Herobrine AI is initializing...");
        ModEntityTypes.registerModEntityTypes();
        ModItems.registerModItems();
        HerobrineManager.initialize();
        HerobrineDeathManager.initialize();
    }
}
