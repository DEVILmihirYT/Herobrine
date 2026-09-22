package com.example.herobrine;

import com.example.HerobrineMod;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;

public final class HerobrineCastleManager {
    private static final int MIN_DISTANCE = 6000;
    private static final int MAX_DISTANCE = 12000;

    private HerobrineCastleManager() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof EnderDragon)
                    || !(source.getEntity() instanceof ServerPlayer killer)
                    || !(entity.level() instanceof ServerLevel endLevel)
                    || endLevel.dimension() != Level.END) {
                return;
            }

            revealCastleCoordinates(endLevel.getServer(), killer);
        });
        HerobrineMod.LOGGER.info("Herobrine castle coordinate trigger initialized");
    }

    public static void revealCastleCoordinates(MinecraftServer server, ServerPlayer killer) {
        HerobrineCastleState state = HerobrineCastleState.get(server);
        if (state.isCoordinatesRevealed()) {
            sendCoordinates(killer, state.getCastlePosition());
            return;
        }

        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return;
        }

        BlockPos spawn = overworld.getRespawnData().pos();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
        double distance = random.nextDouble(MIN_DISTANCE, MAX_DISTANCE + 1.0D);

        int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
        int y = overworld.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                x,
                z
        );

        BlockPos castle = new BlockPos(x, y, z);
        state.revealCoordinates(castle);

        sendCoordinates(killer, castle);

        HerobrineMod.LOGGER.info(
                "Herobrine castle coordinates revealed at {}, {}, {}; schematic generation pending",
                castle.getX(),
                castle.getY(),
                castle.getZ()
        );
    }

    private static void sendCoordinates(ServerPlayer player, BlockPos castle) {
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("CASTLE: " + castle.getX() + " " + castle.getZ())
        ));
        player.sendSystemMessage(Component.literal(
                "Herobrine's Castle coordinates: X=" + castle.getX()
                        + " Y=" + castle.getY() + " Z=" + castle.getZ()
        ));
        player.sendSystemMessage(Component.literal(
                "Find the castle thousands of blocks from world spawn."
        ));
    }
}
