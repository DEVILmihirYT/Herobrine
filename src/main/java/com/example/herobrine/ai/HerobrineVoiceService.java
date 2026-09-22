package com.example.herobrine.ai;

import com.example.HerobrineMod;
import com.example.herobrine.entity.HerobrineEntity;
import com.example.herobrine.network.HerobrineVoicePayload;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class HerobrineVoiceService {
    private static final String API_URL = "https://api.elevenlabs.io/v1/text-to-speech/";
    private static final String MODEL_ID = "eleven_v3";
    private static final int MAX_TEXT_CHARS = 500;
    private static final double HEARING_RANGE = 64.0D;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private HerobrineVoiceService() {
    }

    public static void speak(ServerLevel level, HerobrineEntity hero, String text) {
        String apiKey = System.getenv("ELEVENLABS_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            HerobrineMod.LOGGER.debug("ElevenLabs voice skipped: ELEVENLABS_API_KEY is not configured");
            return;
        }

        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return;
        }

        String voiceId = HerobrineVoiceStage.forStage(hero.getStage()).voiceId();
        String body = "{\"text\":\""
                + escapeJson(normalized)
                + "\",\"model_id\":\""
                + MODEL_ID
                + "\"}";

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + voiceId + "?output_format=pcm_24000"))
                    .timeout(Duration.ofSeconds(30))
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException error) {
            HerobrineMod.LOGGER.warn("Invalid ElevenLabs voice request", error);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<byte[]> response = HTTP.send(
                        request,
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                if (response.statusCode() != 200) {
                    HerobrineMod.LOGGER.warn(
                            "ElevenLabs voice request failed with HTTP {}",
                            response.statusCode()
                    );
                    return;
                }

                byte[] audio = response.body();
                if (audio.length == 0 || audio.length > HerobrineVoicePayload.MAX_AUDIO_BYTES) {
                    HerobrineMod.LOGGER.warn(
                            "ElevenLabs voice response rejected: {} bytes",
                            audio.length
                    );
                    return;
                }

                byte[] immutableAudio = Arrays.copyOf(audio, audio.length);
                level.getServer().execute(() -> sendToNearbyPlayers(level, hero, immutableAudio));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                HerobrineMod.LOGGER.debug("ElevenLabs voice request interrupted");
            } catch (IOException error) {
                HerobrineMod.LOGGER.warn("ElevenLabs voice request failed", error);
            }
        });
    }

    private static void sendToNearbyPlayers(
            ServerLevel level,
            HerobrineEntity hero,
            byte[] audio
    ) {
        double rangeSqr = HEARING_RANGE * HEARING_RANGE;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(hero) <= rangeSqr) {
                ServerPlayNetworking.send(
                        player,
                        new HerobrineVoicePayload(hero.getX(), hero.getY(), hero.getZ(), audio)
                );
            }
        }
    }

    private static String normalize(String text) {
        String value = text
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();

        if (value.length() <= MAX_TEXT_CHARS) {
            return value;
        }

        return value.substring(0, MAX_TEXT_CHARS).trim();
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\\\", "\\\\\\\\")
                .replace("\"", "\\\"");
    }
}
