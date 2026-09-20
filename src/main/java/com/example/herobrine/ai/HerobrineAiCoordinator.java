package com.example.herobrine.ai;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineAction;
import com.example.herobrine.HerobrineChatMemory;
import com.example.herobrine.HerobrineManager;
import com.example.herobrine.HerobrinePlayerTracker;
import com.example.herobrine.HerobrineStage;
import com.example.herobrine.entity.HerobrineEntity;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Provider-neutral asynchronous AI boundary.
 *
 * No HTTP client or model SDK is embedded here. A future Qwen/Llama provider
 * can be attached without changing Minecraft-side behavior or action safety.
 */
public final class HerobrineAiCoordinator implements AutoCloseable {
    private static final int QUEUE_WORKERS = 2;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            QUEUE_WORKERS,
            runnable -> {
                Thread thread = new Thread(runnable, "Herobrine-AI");
                thread.setDaemon(true);
                return thread;
            }
    );

    private volatile HerobrineAiProvider primaryProvider;
    private volatile HerobrineAiProvider fallbackProvider;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public void setProviders(HerobrineAiProvider primary, HerobrineAiProvider fallback) {
        this.primaryProvider = primary;
        this.fallbackProvider = fallback;
    }

    public void requestFromMention(
            ServerLevel level,
            HerobrineEntity hero,
            ServerPlayer player
    ) {
        if (closed.get() || hero == null || !hero.isAlive() || player == null) {
            return;
        }

        HerobrineAiRequest request = new HerobrineAiRequest(
                player.getUUID(),
                player.getName().getString(),
                hero.getStage(),
                "explicit_hero_mention",
                HerobrineManager.recentChat(),
                HerobrineManager.trackedPlayers()
        );

        HerobrineAiProvider primary = primaryProvider;
        HerobrineAiProvider fallback = fallbackProvider;

        if (primary == null && fallback == null) {
            HerobrineMod.LOGGER.debug(
                    "AI trigger queued but no external provider is configured yet for {}",
                    player.getGameProfile().name()
            );
            return;
        }

        CompletableFuture<HerobrineAiDecision> future = submit(primary, request);
        if (fallback != null) {
            future = future.handle((decision, error) -> {
                if (error == null && decision != null) {
                    return CompletableFuture.completedFuture(decision);
                }
                return submit(fallback, request);
            }).thenCompose(value -> value);
        }

        future.whenCompleteAsync(
                (decision, error) -> handleResult(level, hero, player, request, decision, error),
                executor
        );
    }

    private CompletableFuture<HerobrineAiDecision> submit(
            HerobrineAiProvider provider,
            HerobrineAiRequest request
    ) {
        if (provider == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AI provider is not configured")
            );
        }

        try {
            return provider.decide(request);
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private void handleResult(
            ServerLevel level,
            HerobrineEntity hero,
            ServerPlayer player,
            HerobrineAiRequest request,
            HerobrineAiDecision decision,
            Throwable error
    ) {
        if (error != null || decision == null) {
            HerobrineMod.LOGGER.warn("Herobrine AI request failed", error);
            return;
        }

        if (!isCurrentServerStateValid(hero, player, request.stage())) {
            return;
        }

        if (!isActionAllowed(request.stage(), decision.action())) {
            HerobrineMod.LOGGER.warn(
                    "Rejected AI action {} for stage {}",
                    decision.action(),
                    request.stage()
            );
            return;
        }

        // Execution is intentionally left on the Minecraft server thread.
        // The provider thread may never mutate world/entity state directly.
        level.getServer().execute(() -> {
            if (!isCurrentServerStateValid(hero, player, request.stage())) {
                return;
            }

            HerobrineMod.LOGGER.info(
                    "Validated AI decision for {}: {}",
                    player.getGameProfile().name(),
                    decision.action()
            );

            // The existing HerobrineActionExecutor is the only place where
            // concrete game actions should be executed. Provider-specific
            // response parsing will be added when the real AI provider is
            // connected.
        });
    }

    private static boolean isCurrentServerStateValid(
            HerobrineEntity hero,
            ServerPlayer player,
            HerobrineStage requestStage
    ) {
        return hero.isAlive()
                && player.isAlive()
                && hero.getStage() == requestStage
                && hero.level() == player.level();
    }

    private static boolean isActionAllowed(
            HerobrineStage stage,
            HerobrineAction action
    ) {
        if (action == null) {
            return false;
        }

        return switch (stage) {
            case STAGE_1 -> action != HerobrineAction.ATTACK_PLAYER;
            case STAGE_2 -> action != HerobrineAction.ATTACK_PLAYER;
            case STAGE_3 -> true;
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
