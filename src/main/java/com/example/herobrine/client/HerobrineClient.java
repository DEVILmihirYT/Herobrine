package com.example.herobrine.client;

import com.example.herobrine.network.HerobrineVoicePayload;
import com.example.herobrine.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class HerobrineClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ModEntityTypes.HEROBRINE, HerobrineRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(
                HerobrineVoicePayload.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> HerobrineVoicePlayback.play(payload));
                }
        );
    }
}
