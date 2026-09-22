package com.example.herobrine.network;

import com.example.HerobrineMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record HerobrineVoicePayload(
        double x,
        double y,
        double z,
        byte[] pcm
) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            HerobrineMod.MOD_ID,
            "herobrine_voice"
    );
    public static final Type<HerobrineVoicePayload> TYPE = new Type<>(ID);
    public static final int MAX_AUDIO_BYTES = 900_000;

    public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineVoicePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE,
                    HerobrineVoicePayload::x,
                    ByteBufCodecs.DOUBLE,
                    HerobrineVoicePayload::y,
                    ByteBufCodecs.DOUBLE,
                    HerobrineVoicePayload::z,
                    ByteBufCodecs.byteArray(MAX_AUDIO_BYTES),
                    HerobrineVoicePayload::pcm,
                    HerobrineVoicePayload::new
            );

    public HerobrineVoicePayload {
        if (pcm.length > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("Herobrine voice payload is too large");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
