package com.example.herobrine;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class GrudgeManager {
    private static final long MIN_REVENGE_DAYS = 2L;
    private static final long MAX_REVENGE_DAYS = 4L;

    private GrudgeManager() {
    }

    public static void activateGrudge(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        HerobrineWorldState state = HerobrineWorldState.get(server);
        long currentDay = player.level().getGameTime() / 24000L;
        long duration = MIN_REVENGE_DAYS
                + player.level().getRandom().nextInt((int) (MAX_REVENGE_DAYS - MIN_REVENGE_DAYS + 1L));

        state.setGrudgeActive(player.getUUID(), true);
        state.setRevengeUntilDay(player.getUUID(), currentDay + duration);
    }

    public static boolean isActive(MinecraftServer server, UUID playerId) {
        return HerobrineWorldState.get(server).isGrudgeActive(playerId);
    }

    public static long revengeUntilDay(MinecraftServer server, UUID playerId) {
        return HerobrineWorldState.get(server).getRevengeUntilDay(playerId);
    }

    public static void clearGrudge(MinecraftServer server, UUID playerId) {
        HerobrineWorldState.get(server).clearPlayerState(playerId);
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD || level.getGameTime() % 200L != 0L) {
            return;
        }

        HerobrineWorldState state = HerobrineWorldState.get(level.getServer());
        long currentDay = level.getGameTime() / 24000L;

        for (HerobrineWorldState.PlayerState playerState : state.getPlayers()) {
            long revengeUntilDay = playerState.revengeUntilDay();

            if (playerState.grudgeActive()
                    && revengeUntilDay >= 0L
                    && currentDay >= revengeUntilDay) {
                try {
                    UUID playerId = UUID.fromString(playerState.uuid());
                    ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                    if (target != null && target.level() instanceof ServerLevel targetLevel) {
                        HerobrineDeathManager.beginRevenge(target);
                        HerobrineWorldState.get(level.getServer()).clearGrudge(playerId);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted UUIDs are safely ignored.
                }
            }
        }
    }
}
