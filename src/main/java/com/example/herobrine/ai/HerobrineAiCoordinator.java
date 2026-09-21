package com.example.herobrine.ai;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineAction;
import com.example.herobrine.HerobrineActionExecutor;
import com.example.herobrine.HerobrineChatMemory;
import com.example.herobrine.HerobrineManager;
import com.example.herobrine.HerobrinePlayerTracker;
import com.example.herobrine.HerobrineStage;
import com.example.herobrine.entity.HerobrineEntity;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Provider-neutral asynchronous AI boundary.
 *
 * Providers are external HTTP adapters. Minecraft-side behavior and action safety
 * remain independent from any model vendor.
 */
public final class HerobrineAiCoordinator implements AutoCloseable {
    private static final int QUEUE_WORKERS = 2;
    private static final int MAX_PENDING_REQUESTS = 8;
    private static final long MENTION_COOLDOWN_TICKS = 100L;

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
    private volatile HerobrineAiProvider tertiaryProvider;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicInteger pendingRequests =
            new java.util.concurrent.atomic.AtomicInteger();
    private final Map<UUID, Long> lastMentionTick = new ConcurrentHashMap<>();

    public void setProviders(
            HerobrineAiProvider primary,
            HerobrineAiProvider fallback,
            HerobrineAiProvider tertiary
    ) {
        this.primaryProvider = primary;
        this.fallbackProvider = fallback;
        this.tertiaryProvider = tertiary;
    }

    public void requestFromMention(
            ServerLevel level,
            HerobrineEntity hero,
            ServerPlayer player
    ) {
        if (closed.get() || hero == null || !hero.isAlive() || player == null) {
            return;
        }

        com.example.herobrine.HerobrineWorldState worldState =
                com.example.herobrine.HerobrineWorldState.get(level.getServer());
        HerobrineAiRequest request = new HerobrineAiRequest(
                player.getUUID(),
                player.getName().getString(),
                hero.getStage(),
                "explicit_hero_mention",
                HerobrineManager.recentChat(),
                HerobrineManager.trackedPlayers(),
                worldState.getAiMemories(player.getUUID()),
                worldState.getRelationship(player.getUUID())
        );

        HerobrineAiProvider primary = primaryProvider;
        HerobrineAiProvider fallback = fallbackProvider;
        HerobrineAiProvider tertiary = tertiaryProvider;

        if (primary == null && fallback == null && tertiary == null) {
            HerobrineMod.LOGGER.debug(
                    "AI trigger queued but no external provider is configured yet for {}",
                    player.getGameProfile().name()
            );
            return;
        }

        long now = level.getGameTime();
        Long last = lastMentionTick.get(player.getUUID());
        if (last != null && now - last < MENTION_COOLDOWN_TICKS) {
            HerobrineMod.LOGGER.debug("Ignoring repeated AI mention from {} during cooldown", player.getGameProfile().name());
            return;
        }

        if (pendingRequests.get() >= MAX_PENDING_REQUESTS) {
            HerobrineMod.LOGGER.debug("Herobrine AI request queue is full; ignoring trigger from {}", player.getGameProfile().name());
            return;
        }

        lastMentionTick.put(player.getUUID(), now);
        pendingRequests.incrementAndGet();
        CompletableFuture<HerobrineAiDecision> future = submit(primary, request);
        if (fallback != null) {
            future = future.handle((decision, error) -> {
                if (error == null && decision != null) {
                    return CompletableFuture.completedFuture(decision);
                }
                return submit(fallback, request);
            }).thenCompose(value -> value);
        }
        if (tertiary != null) {
            future = future.handle((decision, error) -> {
                if (error == null && decision != null) {
                    return CompletableFuture.completedFuture(decision);
                }
                return submit(tertiary, request);
            }).thenCompose(value -> value);
        }

        future.whenCompleteAsync(
                (decision, error) -> {
                    try {
                        handleResult(level, hero, player, request, decision, error);
                    } finally {
                        pendingRequests.decrementAndGet();
                    }
                },
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

            executeDecision(level, hero, player, decision);

            if (decision.memoryNote() != null && !decision.memoryNote().isBlank()) {
                com.example.herobrine.HerobrineWorldState worldState =
                        com.example.herobrine.HerobrineWorldState.get(level.getServer());
                worldState.recordAiMemory(
                        player.getUUID(),
                        decision.memoryNote(),
                        Math.max(-10, Math.min(10, decision.relationshipDelta())),
                        level.getGameTime() / 24000L
                );
            }
        });
    }

    private static void executeDecision(
            ServerLevel level,
            HerobrineEntity hero,
            ServerPlayer player,
            HerobrineAiDecision decision
    ) {
        switch (decision.action()) {
            case SPEAK -> {
                if (decision.speech() != null && !decision.speech().isBlank()) {
                    player.sendSystemMessage(Component.literal(decision.speech()));
                }
            }
            case OBSERVE -> {
                // Observation intentionally has no visible side effect.
            }
            case DESPAWN -> hero.discard();
            case ATTACK_PLAYER -> {
                ServerPlayer target = findPlayer(level, decision.targetPlayer());
                if (target != null) {
                    HerobrineActionExecutor.attackPlayer(hero, target);
                }
            }
            case GIVE_ITEM -> {
                Item item = resolveGift(decision.itemId());
                if (item != null) {
                    HerobrineActionExecutor.giveItem(
                            hero,
                            player,
                            item,
                            Math.max(1, decision.itemCount())
                    );
                }
            }
            default -> HerobrineMod.LOGGER.debug(
                    "AI action {} is validated but has no executor adapter yet",
                    decision.action()
            );
        }
    }

    private static ServerPlayer findPlayer(ServerLevel level, UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(playerId);
    }

    private static Item resolveGift(String itemId) {
        if (itemId == null) {
            return null;
        }

        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }

        return switch (id) {
            case "torch" -> Items.TORCH;
            case "golden_apple" -> Items.GOLDEN_APPLE;
            case "cooked_beef" -> Items.COOKED_BEEF;
            case "stone_pickaxe" -> Items.STONE_PICKAXE;
            case "stone_axe" -> Items.STONE_AXE;
            case "stone_shovel" -> Items.STONE_SHOVEL;
            case "stone_hoe" -> Items.STONE_HOE;
            case "leather_helmet" -> Items.LEATHER_HELMET;
            case "leather_chestplate" -> Items.LEATHER_CHESTPLATE;
            case "leather_leggings" -> Items.LEATHER_LEGGINGS;
            case "leather_boots" -> Items.LEATHER_BOOTS;
            default -> null;
        };
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
            case STAGE_1 -> switch (action) {
                case SPEAK, OBSERVE, APPROACH, FOLLOW, HIDE, RETREAT,
                        GIVE_ITEM, ATTACK_HOSTILE, TELEPORT_BEHIND,
                        DESPAWN, TRIGGER_ENCOUNTER -> true;
                default -> false;
            };
            case STAGE_2 -> switch (action) {
                case SPEAK, OBSERVE, APPROACH, FOLLOW, HIDE, RETREAT,
                        GIVE_ITEM, ATTACK_HOSTILE, STEAL_LOOT, BURN_DROP,
                        BREAK_BLOCK, TELEPORT_BEHIND, DESPAWN,
                        TRIGGER_ENCOUNTER -> true;
                default -> false;
            };
            case STAGE_3 -> switch (action) {
                case SPEAK, OBSERVE, APPROACH, FOLLOW, HIDE, RETREAT,
                        ATTACK_PLAYER, TELEPORT_BEHIND, DESPAWN,
                        TRIGGER_ENCOUNTER -> true;
                default -> false;
            };
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
            lastMentionTick.clear();
        }
    }
}
