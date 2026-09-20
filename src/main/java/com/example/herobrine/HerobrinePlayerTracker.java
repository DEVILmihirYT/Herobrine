package com.example.herobrine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-local player observation layer.
 *
 * This class deliberately does not call an external AI provider. It keeps a
 * compact, current snapshot of players so a future AI adapter can consume
 * summarized state instead of raw per-tick movement events.
 */
public final class HerobrinePlayerTracker {
    public static final int MAX_TRACKED_PLAYERS = 32;

    public record Snapshot(
            UUID uuid,
            String name,
            String dimension,
            double x,
            double y,
            double z,
            double distanceToHero,
            boolean alive,
            boolean creative,
            boolean operator,
            long observedGameTime
    ) {
    }

    private final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public void observe(ServerLevel level, HerobrineEntityView hero) {
        List<ServerPlayer> players = new ArrayList<>(level.players());

        for (ServerPlayer player : players) {
            double distance = hero.distanceToSqr(player);
            snapshots.put(
                    player.getUUID(),
                    new Snapshot(
                            player.getUUID(),
                            player.getName().getString(),
                            level.dimension().location().toString(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            Math.sqrt(distance),
                            player.isAlive(),
                            player.gameMode().isCreative(),
                            player.permissions().hasPermission(
                                    net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR
                            ),
                            level.getGameTime()
                    )
            );
        }

        trim();
    }

    public List<Snapshot> snapshots() {
        return snapshots.values().stream()
                .sorted(Comparator.comparing(Snapshot::name))
                .toList();
    }

    public Snapshot get(UUID playerId) {
        return snapshots.get(playerId);
    }

    public void remove(UUID playerId) {
        snapshots.remove(playerId);
    }

    private void trim() {
        while (snapshots.size() > MAX_TRACKED_PLAYERS) {
            UUID oldest = snapshots.keySet().iterator().next();
            snapshots.remove(oldest);
        }
    }

    /**
     * Minimal abstraction keeps the tracker independent from the concrete
     * entity implementation and makes future AI context tests easier.
     */
    public interface HerobrineEntityView {
        double distanceToSqr(ServerPlayer player);
    }
}
