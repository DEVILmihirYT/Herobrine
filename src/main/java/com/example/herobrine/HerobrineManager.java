package com.example.herobrine;

import com.example.HerobrineMod;
import com.example.herobrine.entity.HerobrineEntity;
import com.example.herobrine.entity.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class HerobrineManager {
    private static final double TRACK_RANGE = 100.0D;
    private static final int NIGHT_SPAWN_CHECK_INTERVAL = 200;

    private HerobrineManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(HerobrineManager::tickLevel);
        ServerMessageEvents.CHAT_MESSAGE.register(
                (message, sender, chatType) -> handleChatMention(message, sender)
        );
        HerobrineMod.LOGGER.info("Herobrine manager initialized");
    }

    private static void tickLevel(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        List<HerobrineEntity> nearbyEntities = collectNearbyHerobrines(level);

        for (HerobrineEntity herobrine : nearbyEntities) {
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && herobrine.distanceToSqr(player) <= TRACK_RANGE * TRACK_RANGE) {
                    herobrine.enforcePlayerRestrictions(player);
                }
            }
        }

        // Lightweight Stage 1 appearance chance during nighttime.
        if (nearbyEntities.isEmpty()
                && isNight(level)
                && level.getGameTime() % NIGHT_SPAWN_CHECK_INTERVAL == 0L
                && !level.players().isEmpty()
                && level.getRandom().nextInt(8) == 0) {
            spawnStage1(level);
        }
    }

    private static List<HerobrineEntity> collectNearbyHerobrines(ServerLevel level) {
        List<HerobrineEntity> result = new ArrayList<>();

        for (ServerPlayer player : level.players()) {
            result.addAll(level.getEntitiesOfClass(
                    HerobrineEntity.class,
                    player.getBoundingBox().inflate(TRACK_RANGE + 16.0D),
                    entity -> entity.isAlive()
            ));
        }

        return result.stream().distinct().toList();
    }

    private static void handleChatMention(PlayerChatMessage message, ServerPlayer sender) {
        if (!containsPublicMention(message.signedContent())) {
            return;
        }

        activateStage3FromMention(sender);
    }

    private static boolean containsPublicMention(String text) {
        String[] tokens = text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .split("\\s+");

        for (String token : tokens) {
            if (token.equals("hero") || token.equals("herobrine")) {
                return true;
            }
        }

        return false;
    }

    private static void activateStage3FromMention(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        HerobrineEntity herobrine = findNearest(level, player, TRACK_RANGE);

        if (herobrine == null) {
            herobrine = spawnStage3(level, player);
        } else {
            herobrine.setStage(HerobrineStage.STAGE_3);
            herobrine.markStage3Activated(level.getGameTime() / 24000L);
            moveBehindPlayer(level, herobrine, player, true);
        }

        if (herobrine != null) {
            herobrine.setTarget(player);
        }
    }

    private static HerobrineEntity spawnStage1(ServerLevel level) {
        ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));
        BlockPos spawnPos = findBehindPlayerPosition(level, player, 12.0D, 24.0D);

        HerobrineEntity entity = ModEntityTypes.HEROBRINE.spawn(
                level,
                spawnPos,
                EntitySpawnReason.NATURAL
        );

        if (entity != null) {
            entity.setStage(HerobrineStage.STAGE_1);
        }

        return entity;
    }

    private static HerobrineEntity spawnStage3(ServerLevel level, ServerPlayer player) {
        BlockPos spawnPos = findBehindPlayerPosition(level, player, 20.0D, 30.0D);

        HerobrineEntity entity = ModEntityTypes.HEROBRINE.spawn(
                level,
                spawnPos,
                EntitySpawnReason.TRIGGERED
        );

        if (entity != null) {
            entity.setStage(HerobrineStage.STAGE_3);
            entity.markStage3Activated(level.getGameTime() / 24000L);
            moveBehindPlayer(level, entity, player, true);
        }

        return entity;
    }

    private static void moveBehindPlayer(
            ServerLevel level,
            HerobrineEntity entity,
            ServerPlayer player,
            boolean preferHidden
    ) {
        BlockPos position = findBehindPlayerPosition(level, player, 20.0D, 30.0D);
        entity.setPos(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D
        );
        entity.setRot(player.getYRot(), 0.0F);

        if (preferHidden && player.hasLineOfSight(entity)) {
            for (int i = 0; i < 6 && player.hasLineOfSight(entity); i++) {
                position = findBehindPlayerPosition(level, player, 20.0D, 30.0D);
                entity.setPos(
                        position.getX() + 0.5D,
                        position.getY(),
                        position.getZ() + 0.5D
                );
                entity.setRot(player.getYRot(), 0.0F);
            }
        }
    }

    private static BlockPos findBehindPlayerPosition(
            ServerLevel level,
            ServerPlayer player,
            double minDistance,
            double maxDistance
    ) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);

        if (horizontal.lengthSqr() < 0.001D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        double distance = minDistance + level.random.nextDouble() * (maxDistance - minDistance);
        double x = player.getX() - horizontal.x * distance;
        double z = player.getZ() - horizontal.z * distance;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                blockX,
                blockZ
        );

        BlockPos candidate = new BlockPos(blockX, groundY, blockZ);
        BlockState state = level.getBlockState(candidate);

        if (!state.getCollisionShape(level, candidate).isEmpty()) {
            candidate = candidate.above();
        }

        return candidate;
    }

    private static boolean isNight(ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000L;
        return timeOfDay >= 13000L && timeOfDay < 23000L;
    }

    private static HerobrineEntity findNearest(
            ServerLevel level,
            ServerPlayer player,
            double range
    ) {
        double rangeSqr = range * range;
        HerobrineEntity nearest = null;
        double nearestDistanceSqr = rangeSqr;

        for (HerobrineEntity entity : level.getEntitiesOfClass(
                HerobrineEntity.class,
                player.getBoundingBox().inflate(range),
                HerobrineEntity::isAlive
        )) {
            double distanceSqr = entity.distanceToSqr(player);
            if (distanceSqr <= nearestDistanceSqr) {
                nearest = entity;
                nearestDistanceSqr = distanceSqr;
            }
        }

        return nearest;
    }
}
