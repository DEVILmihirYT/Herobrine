package com.example.herobrine.block;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineWorldState;
import com.example.herobrine.entity.HerobrineEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

public final class HerobrineDoomsdayManager {
    private static final int COUNTDOWN_TICKS = 200;
    private static final int DESTROY_RADIUS = 16;

    private static MinecraftServer server;
    private static ServerBossEvent bossBar;
    private static int ticksRemaining = -1;
    private static boolean running;
    private static List<BlockPos> portalBlocks = List.of();
    private static UUID winnerPlayer;

    private HerobrineDoomsdayManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(HerobrineDoomsdayManager::tick);
    }

    public static void begin(
            MinecraftServer minecraftServer,
            HerobrineEntity hero,
            net.minecraft.server.level.ServerLevel level,
            BlockPos portalPos
    ) {
        if (running || HerobrineWorldState.get(minecraftServer).isPermanentlyDefeated()) {
            return;
        }

        List<BlockPos> detectedPortal = DarkPortalBlock.collectPortalBlocks(level, portalPos);
        if (detectedPortal.isEmpty()) {
            return;
        }

        server = minecraftServer;
        running = true;
        ticksRemaining = COUNTDOWN_TICKS;
        portalBlocks = List.copyOf(detectedPortal);
        winnerPlayer = findNearestPlayer(level, portalPos);

        bossBar = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal("HEROBRINE — DOOMSDAY — 10s"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setProgress(1.0F);
        bossBar.setDarkenScreen(true);
        bossBar.setCreateWorldFog(true);

        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            bossBar.addPlayer(player);
            player.sendSystemMessage(Component.literal(
                    "HEROBRINE has entered the Dark Portal. Destroy the portal before 10 seconds end."
            ));
        }

        hero.discard();
        HerobrineMod.LOGGER.warn("Herobrine Doomsday countdown started");
    }

    private static void tick(MinecraftServer minecraftServer) {
        if (!running || minecraftServer != server) {
            return;
        }

        if (bossBar != null) {
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }

        if (minecraftServer.getTickCount() % 20 != 0) {
            return;
        }

        if (!isPortalStillPresent()) {
            playerWins();
            return;
        }

        ticksRemaining -= 20;
        if (bossBar != null) {
            int seconds = Math.max(0, (ticksRemaining + 19) / 20);
            bossBar.setProgress(Math.max(0.0F, ticksRemaining / (float) COUNTDOWN_TICKS));
            bossBar.setName(Component.literal("HEROBRINE — DOOMSDAY — " + seconds + "s"));
        }

        if (ticksRemaining <= 0) {
            executeDoomsday();
        }
    }

    private static boolean isPortalStillPresent() {
        if (server == null || portalBlocks.isEmpty()) {
            return false;
        }

        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : portalBlocks) {
                if (level.getBlockState(pos).getBlock() == ModBlocks.DARK_PORTAL) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void playerWins() {
        if (server == null) {
            return;
        }

        HerobrineWorldState.get(server).markPermanentlyDefeated();
        removeRemainingPortalBlocks();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (winnerPlayer != null && winnerPlayer.equals(player.getUUID())) {
                player.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("YOU WIN")
                ));
                player.sendSystemMessage(Component.literal(
                        "You destroyed the Dark Portal. Herobrine is defeated."
                ));
            } else {
                player.sendSystemMessage(Component.literal(
                        "The Dark Portal has been destroyed. Herobrine is defeated."
                ));
            }
        }

        cleanup();
        HerobrineMod.LOGGER.info("Dark Portal destroyed before Doomsday; player victory recorded");
    }

    private static void executeDoomsday() {
        if (server == null) {
            return;
        }

        HerobrineWorldState state = HerobrineWorldState.get(server);
        state.markPermanentlyDefeated();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("HEROBRINE")
            ));
            player.sendSystemMessage(Component.literal(
                    "HEROBRINE: DOOMSDAY."
            ));
            destroyBoundedArea(player);
        }

        removeRemainingPortalBlocks();
        cleanup();
        HerobrineMod.LOGGER.warn("Herobrine Doomsday completed; defeat is permanent");
    }

    private static void removeRemainingPortalBlocks() {
        if (server == null) {
            return;
        }

        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : portalBlocks) {
                if (level.getBlockState(pos).getBlock() == ModBlocks.DARK_PORTAL) {
                    level.removeBlock(pos, false);
                }
            }
        }
    }

    private static UUID findNearestPlayer(
            net.minecraft.server.level.ServerLevel level,
            BlockPos center
    ) {
        UUID nearest = null;
        double best = 16.0D * 16.0D;

        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(
                    center.getX() + 0.5D,
                    center.getY() + 0.5D,
                    center.getZ() + 0.5D
            );
            if (distance <= best) {
                best = distance;
                nearest = player.getUUID();
            }
        }

        return nearest;
    }

    private static void cleanup() {
        if (bossBar != null) {
            for (ServerPlayer player : new ArrayList<>(bossBar.getPlayers())) {
                bossBar.removePlayer(player);
            }
            bossBar.setVisible(false);
        }

        running = false;
        ticksRemaining = -1;
        portalBlocks = List.of();
        winnerPlayer = null;
        bossBar = null;
    }

    private static void destroyBoundedArea(ServerPlayer player) {
        var level = player.level();
        var center = player.blockPosition();

        for (int dx = -DESTROY_RADIUS; dx <= DESTROY_RADIUS; dx++) {
            for (int dy = -DESTROY_RADIUS; dy <= DESTROY_RADIUS; dy++) {
                for (int dz = -DESTROY_RADIUS; dz <= DESTROY_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > DESTROY_RADIUS * DESTROY_RADIUS) {
                        continue;
                    }

                    var pos = center.offset(dx, dy, dz);
                    if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
                        level.destroyBlock(pos, false, player, 0);
                    }
                }
            }
        }
    }
}
