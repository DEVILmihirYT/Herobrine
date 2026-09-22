package com.example.herobrine.client;

import com.example.herobrine.network.HerobrineVoicePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class HerobrineClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                HerobrineVoicePayload.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> HerobrineVoicePlayback.play(payload));
                }
        );
    }
}
