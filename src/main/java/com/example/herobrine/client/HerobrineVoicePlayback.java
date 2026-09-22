package com.example.herobrine.client;

import com.example.herobrine.network.HerobrineVoicePayload;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import javax.sound.sampled.AudioFormat;

public final class HerobrineVoicePlayback {
    private static final Identifier VOICE_SOUND = Identifier.fromNamespaceAndPath(
            "herobrineai",
            "herobrine_voice"
    );

    private HerobrineVoicePlayback() {
    }

    public static void play(HerobrineVoicePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        client.getSoundManager().play(new DynamicVoiceSoundInstance(payload));
    }

    private static final class DynamicVoiceSoundInstance extends AbstractSoundInstance {
        private final byte[] pcm;
        private final double sourceX;
        private final double sourceY;
        private final double sourceZ;

        private DynamicVoiceSoundInstance(HerobrineVoicePayload payload) {
            super(VOICE_SOUND, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.pcm = payload.pcm();
            this.sourceX = payload.x();
            this.sourceY = payload.y();
            this.sourceZ = payload.z();
            this.looping = false;
            this.delay = 0;
            this.relative = false;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.x = sourceX;
            this.y = sourceY;
            this.z = sourceZ;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(
                SoundBufferLibrary loader,
                Identifier id,
                boolean repeatInstantly
        ) {
            return CompletableFuture.completedFuture(new PcmAudioStream(pcm));
        }
    }

    private static final class PcmAudioStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(
                24000.0F,
                16,
                1,
                true,
                false
        );

        private final byte[] data;
        private int position;

        private PcmAudioStream(byte[] data) {
            this.data = data;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public java.nio.ByteBuffer read(int size) {
            if (position >= data.length) {
                return java.nio.ByteBuffer.allocate(0);
            }

            int length = Math.min(size, data.length - position);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data, position, length).slice();
            position += length;
            return buffer;
        }

        @Override
        public void close() {
            position = data.length;
        }
    }
}
