package com.example.herobrine.block;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineWorldState;
import com.example.herobrine.entity.HerobrineEntity;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class HerobrineDoomsdayManager {
    private static final int COUNTDOWN_TICKS = 200;
    private static final int DESTROY_RADIUS = 16;

    private static MinecraftServer server;
    private static ServerBossEvent bossBar;
    private static int ticksRemaining = -1;
    private static boolean running;

    private HerobrineDoomsdayManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(HerobrineDoomsdayManager::tick);
    }

    public static void begin(MinecraftServer minecraftServer, HerobrineEntity hero) {
        if (running || HerobrineWorldState.get(minecraftServer).isPermanentlyDefeated()) {
            return;
        }

        server = minecraftServer;
        running = true;
        ticksRemaining = COUNTDOWN_TICKS;
        bossBar = new ServerBossEvent(
                java.util.UUID.randomUUID(),
                Component.literal("HEROBRINE — DOOMSDAY"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setProgress(1.0F);
        bossBar.setDarkenScreen(true);
        bossBar.setCreateWorldFog(true);

        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            bossBar.addPlayer(player);
            player.sendSystemMessage(Component.literal(
                    "HEROBRINE: The world has 10 seconds."
            ));
        }

        hero.discard();
        HerobrineMod.LOGGER.warn("Herobrine Doomsday countdown started");
    }

    private static void tick(MinecraftServer minecraftServer) {
        if (!running || minecraftServer != server) {
            return;
        }

        if (minecraftServer.getTickCount() % 20 != 0) {
            return;
        }

        ticksRemaining -= 20;
        if (bossBar != null) {
            bossBar.setProgress(Math.max(0.0F, ticksRemaining / (float) COUNTDOWN_TICKS));
        }

        if (ticksRemaining <= 0) {
            executeDoomsday();
        }
    }

    private static void executeDoomsday() {
        if (server == null) {
            return;
        }

        HerobrineWorldState state = HerobrineWorldState.get(server);
        state.markPermanentlyDefeated();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(
                    "HEROBRINE: DOOMSDAY."
            ));
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            destroyBoundedArea(player);
        }

        if (bossBar != null) {
            for (ServerPlayer player : new java.util.ArrayList<>(bossBar.getPlayers())) {
                bossBar.removePlayer(player);
            }
            bossBar.setVisible(false);
        }

        running = false;
        ticksRemaining = -1;
        bossBar = null;
        HerobrineMod.LOGGER.warn("Herobrine Doomsday completed; defeat is permanent");
    }

    private static void destroyBoundedArea(ServerPlayer player) {
        var level = player.level();
        var center = player.blockPosition();
        Set<Long> protectedChunks = new HashSet<>();

        for (int dx = -DESTROY_RADIUS; dx <= DESTROY_RADIUS; dx++) {
            for (int dz = -DESTROY_RADIUS; dz <= DESTROY_RADIUS; dz++) {
                if (dx * dx + dz * dz > DESTROY_RADIUS * DESTROY_RADIUS) {
                    continue;
                }

                var pos = center.offset(dx, 0, dz);
                if (level.isLoaded(pos)) {
                    level.destroyBlock(pos, false, player, 0);
                }
            }
        }
    }
}
