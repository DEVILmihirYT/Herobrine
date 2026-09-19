package com.example.herobrine;

import com.example.herobrine.entity.HerobrineEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class HerobrineStageTwoBehavior {
    private static final double PLAYER_RANGE = 100.0D;
    private static final double ACTION_RANGE = 12.0D;
    private static final int ACTION_INTERVAL = 40;

    private HerobrineStageTwoBehavior() {
    }

    public static void tick(ServerLevel level, HerobrineEntity herobrine) {
        if (herobrine.getStage() != HerobrineStage.STAGE_2
                || herobrine.tickCount % ACTION_INTERVAL != 0
                || !isNight(level)) {
            return;
        }

        ServerPlayer player = findNearestPlayer(level, herobrine);
        if (player == null) {
            return;
        }

        // Keep Stage 2 interactions rare and distributed instead of scanning the
        // whole area every tick.
        int roll = herobrine.getRandom().nextInt(100);

        if (roll < 35) {
            attackNearbyHostile(level, herobrine, player);
        } else if (roll < 60) {
            burnLooseDrop(level, herobrine, player);
        } else if (roll < 85) {
            disturbNearbyLeaves(level, herobrine, player);
        } else {
            stealFromNearbyChest(level, herobrine, player);
        }
    }

    private static void attackNearbyHostile(
            ServerLevel level,
            HerobrineEntity herobrine,
            ServerPlayer player
    ) {
        List<Monster> monsters = level.getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(ACTION_RANGE),
                monster -> monster.isAlive() && monster != herobrine
        );

        if (monsters.isEmpty()) {
            return;
        }

        Monster target = monsters.get(herobrine.getRandom().nextInt(monsters.size()));
        herobrine.doHurtTarget(level, target);
    }

    private static void burnLooseDrop(
            ServerLevel level,
            HerobrineEntity herobrine,
            ServerPlayer player
    ) {
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(8.0D),
                item -> item.isAlive()
        );

        if (drops.isEmpty()) {
            return;
        }

        ItemEntity drop = drops.get(herobrine.getRandom().nextInt(drops.size()));
        drop.setOnFireForTicks(60);
    }

    private static void disturbNearbyLeaves(
            ServerLevel level,
            HerobrineEntity herobrine,
            ServerPlayer player
    ) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = randomNearbyBlock(level, herobrine, player, 8);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof LeavesBlock)) {
                continue;
            }

            if (state.hasProperty(LeavesBlock.PERSISTENT)
                    && state.getValue(LeavesBlock.PERSISTENT)) {
                continue;
            }

            level.destroyBlock(pos, true, herobrine, 3);
            return;
        }
    }

    private static void stealFromNearbyChest(
            ServerLevel level,
            HerobrineEntity herobrine,
            ServerPlayer player
    ) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = randomNearbyBlock(level, herobrine, player, 8);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof ChestBlock)) {
                continue;
            }

            if (!(level.getBlockEntity(pos) instanceof Container container)) {
                continue;
            }

            List<Integer> occupiedSlots = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    occupiedSlots.add(slot);
                }
            }

            if (occupiedSlots.isEmpty() || !herobrine.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                return;
            }

            int slot = occupiedSlots.get(herobrine.getRandom().nextInt(occupiedSlots.size()));
            int amount = Math.min(2, container.getItem(slot).getCount());
            herobrine.setItemSlot(
                    EquipmentSlot.MAINHAND,
                    container.removeItem(slot, amount)
            );
            return;
        }
    }

    private static BlockPos randomNearbyBlock(
            ServerLevel level,
            HerobrineEntity herobrine,
            ServerPlayer player,
            int horizontalRange
    ) {
        int x = player.blockPosition().getX()
                + herobrine.getRandom().nextInt(horizontalRange * 2 + 1)
                - horizontalRange;
        int y = player.blockPosition().getY()
                + herobrine.getRandom().nextInt(9) - 4;
        int z = player.blockPosition().getZ()
                + herobrine.getRandom().nextInt(horizontalRange * 2 + 1)
                - horizontalRange;

        return new BlockPos(x, y, z);
    }

    private static ServerPlayer findNearestPlayer(ServerLevel level, HerobrineEntity herobrine) {
        double rangeSqr = PLAYER_RANGE * PLAYER_RANGE;
        ServerPlayer nearest = null;
        double nearestDistanceSqr = rangeSqr;

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive()) {
                continue;
            }

            double distanceSqr = herobrine.distanceToSqr(player);
            if (distanceSqr <= nearestDistanceSqr) {
                nearest = player;
                nearestDistanceSqr = distanceSqr;
            }
        }

        return nearest;
    }

    private static boolean isNight(ServerLevel level) {
        long timeOfDay = level.getGameTime() % 24000L;
        return timeOfDay >= 13000L && timeOfDay < 23000L;
    }
}
