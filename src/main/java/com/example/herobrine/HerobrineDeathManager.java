package com.example.herobrine;

import com.example.HerobrineMod;
import com.example.herobrine.entity.HerobrineEntity;
import com.example.herobrine.entity.ModEntityTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HerobrineDeathManager {
    private static final double NEAR_RESPAWN_RANGE = 128.0D;
    private static final double HEROBRINE_RANGE = 96.0D;
    private static final int MIN_FOLLOW_KILLS = 2;
    private static final int MAX_FOLLOW_KILLS = 3;

    private static final Map<UUID, DeathSequence> SEQUENCES = new HashMap<>();

    private HerobrineDeathManager() {
    }

    public static void beginRevenge(ServerPlayer player) {
        if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        HerobrineEntity herobrine = findOrSpawnHerobrine(player);
        if (herobrine == null) {
            return;
        }

        herobrine.setStage(HerobrineStage.STAGE_3);
        herobrine.markStage3Activated(level.getGameTime() / 24000L);
        herobrine.setTarget(player);
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "Herobrine: You thought time would make me forget."
                )
        );

        player.setHealth(1.0F);
        herobrine.doHurtTarget(level, player);
        if (player.isAlive()) {
            player.kill(level);
        }
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register(HerobrineDeathManager::afterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(HerobrineDeathManager::afterRespawn);
        HerobrineMod.LOGGER.info("Herobrine death/respawn manager initialized");
    }

    private static void afterDeath(
            net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source
    ) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (!(source.getEntity() instanceof HerobrineEntity herobrine)
                || herobrine.getStage() != HerobrineStage.STAGE_3) {
            return;
        }

        UUID playerId = player.getUUID();
        DeathSequence existing = SEQUENCES.get(playerId);

        if (existing != null) {
            return;
        }

        int remainingKills = 2 + ((ServerLevel) player.level()).getRandom().nextInt(2);
        SEQUENCES.put(
                playerId,
                new DeathSequence(
                        player.position(),
                        ((ServerLevel) player.level()).dimension(),
                        remainingKills,
                        false
                )
        );

        HerobrineMod.LOGGER.info(
                "Stage 3 death sequence armed for {} with {} follow-up kills",
                player.getGameProfile().name(),
                remainingKills
        );
    }

    private static void afterRespawn(
            ServerPlayer oldPlayer,
            ServerPlayer newPlayer,
            boolean alive
    ) {
        DeathSequence sequence = SEQUENCES.get(newPlayer.getUUID());
        if (sequence == null) {
            return;
        }

        if (((ServerLevel) newPlayer.level()).dimension() != sequence.deathDimension()) {
            finishSequence(newPlayer);
            return;
        }

        Vec3 respawnPosition = newPlayer.position();
        double distanceFromDeath = respawnPosition.distanceTo(sequence.deathPosition());

        boolean worldSpawn = isNearWorldSpawn(((ServerLevel) newPlayer.level()), respawnPosition);
        boolean nearbyBedOrBase = !worldSpawn && distanceFromDeath <= NEAR_RESPAWN_RANGE;

        if (!worldSpawn && !nearbyBedOrBase) {
            HerobrineMod.LOGGER.info(
                    "Stage 3 respawn sequence aborted for {} because respawn was too far away",
                    newPlayer.getGameProfile().name()
            );
            discardNearbyHerobrines(newPlayer);
            finishSequence(newPlayer);
            return;
        }

        HerobrineEntity herobrine = findOrSpawnHerobrine(newPlayer);
        if (herobrine == null) {
            finishSequence(newPlayer);
            return;
        }

        herobrine.setStage(HerobrineStage.STAGE_3);
        herobrine.markStage3Activated(((ServerLevel) newPlayer.level()).getGameTime() / 24000L);
        herobrine.setTarget(newPlayer);
        herobrine.setPos(
                newPlayer.getX() + 2.0D,
                newPlayer.getY(),
                newPlayer.getZ() + 2.0D
        );

        if (nearbyBedOrBase) {
            if (!sequence.baseDamaged()) {
                damageNearbyBase((ServerLevel) newPlayer.level(), herobrine, newPlayer.blockPosition());
                sequence = sequence.withBaseDamaged(true);
                SEQUENCES.put(newPlayer.getUUID(), sequence);
            }
            announceBaseEncounter(newPlayer);
        } else {
            newPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Herobrine: You came back. I am not finished."
                    )
            );
        }

        int remainingKills = sequence.remainingKills();
        if (remainingKills <= 0) {
            newPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Herobrine: I will see you later."
                    )
            );
            finishSequence(newPlayer);
            return;
        }

        SEQUENCES.put(
                newPlayer.getUUID(),
                sequence.withRemainingKills(remainingKills - 1)
        );

        newPlayer.kill(((ServerLevel) newPlayer.level()));
    }

    private static boolean isNearWorldSpawn(ServerLevel level, Vec3 position) {
        BlockPos spawn = level.getRespawnData().pos();
        double dx = position.x - (spawn.getX() + 0.5D);
        double dz = position.z - (spawn.getZ() + 0.5D);
        return dx * dx + dz * dz <= 32.0D * 32.0D;
    }

    private static HerobrineEntity findOrSpawnHerobrine(ServerPlayer player) {
        ServerLevel level = ((ServerLevel) player.level());

        for (HerobrineEntity entity : level.getEntitiesOfClass(
                HerobrineEntity.class,
                player.getBoundingBox().inflate(HEROBRINE_RANGE),
                entity -> entity.isAlive() && entity.getStage() == HerobrineStage.STAGE_3
        )) {
            return entity;
        }

        BlockPos spawn = player.blockPosition().offset(2, 0, 2);
        HerobrineEntity entity = ModEntityTypes.HEROBRINE.spawn(
                level,
                spawn,
                EntitySpawnReason.TRIGGERED
        );

        if (entity != null) {
            entity.setStage(HerobrineStage.STAGE_3);
        }

        return entity;
    }

    private static void discardNearbyHerobrines(ServerPlayer player) {
        ServerLevel level = ((ServerLevel) player.level());

        for (HerobrineEntity entity : level.getEntitiesOfClass(
                HerobrineEntity.class,
                player.getBoundingBox().inflate(HEROBRINE_RANGE),
                HerobrineEntity::isAlive
        )) {
            if (entity.getStage() == HerobrineStage.STAGE_3) {
                entity.discard();
            }
        }
    }

    private static void damageNearbyBase(
            ServerLevel level,
            HerobrineEntity herobrine,
            BlockPos center
    ) {
        int broken = 0;

        for (int attempt = 0; attempt < 32 && broken < 5; attempt++) {
            int x = center.getX() + herobrine.getRandom().nextInt(7) - 3;
            int y = center.getY() + herobrine.getRandom().nextInt(3) - 1;
            int z = center.getZ() + herobrine.getRandom().nextInt(7) - 3;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }

            if (level.destroyBlock(pos, true, herobrine, 3)) {
                broken++;
            }
        }

        if (broken > 0) {
            level.explode(
                    herobrine,
                    center.getX() + 0.5D,
                    center.getY() + 0.5D,
                    center.getZ() + 0.5D,
                    1.5F,
                    false,
                    Level.ExplosionInteraction.MOB
            );
        }
    }

    private static void announceBaseEncounter(ServerPlayer player) {
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "Herobrine: This place is not safe either."
                )
        );
    }

    private static void finishSequence(ServerPlayer player) {
        SEQUENCES.remove(player.getUUID());
    }

    private record DeathSequence(
            Vec3 deathPosition,
            net.minecraft.resources.ResourceKey<Level> deathDimension,
            int remainingKills,
            boolean baseDamaged
    ) {
        private DeathSequence withRemainingKills(int value) {
            return new DeathSequence(deathPosition, deathDimension, value, baseDamaged);
        }

        private DeathSequence withBaseDamaged(boolean value) {
            return new DeathSequence(deathPosition, deathDimension, remainingKills, value);
        }
    }
}
