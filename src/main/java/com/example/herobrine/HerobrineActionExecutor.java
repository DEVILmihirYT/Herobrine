package com.example.herobrine;

import com.example.herobrine.entity.HerobrineEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class HerobrineActionExecutor {
    private static final int MAX_GOLDEN_APPLES_PER_GIFT = 5;
    private static final int MAX_STOLEN_ITEMS = 2;

    private HerobrineActionExecutor() {
    }

    public static boolean giveGoldenApples(HerobrineEntity herobrine, ServerPlayer player, int count) {
        if (!canAssist(herobrine) || count < 1) {
            return false;
        }

        int amount = Math.min(count, MAX_GOLDEN_APPLES_PER_GIFT);
        return giveItem(player, new ItemStack(Items.GOLDEN_APPLE, amount));
    }

    public static boolean giveTorch(HerobrineEntity herobrine, ServerPlayer player) {
        if (!canAssist(herobrine)) {
            return false;
        }

        return giveItem(player, new ItemStack(Items.TORCH));
    }

    public static boolean giveItem(HerobrineEntity herobrine, ServerPlayer player, Item item, int count) {
        if (!canAssist(herobrine) || item == null || count <= 0) {
            return false;
        }

        if (!isAllowedGift(item)) {
            return false;
        }

        return giveItem(player, new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize())));
    }

    public static boolean attackHostile(HerobrineEntity herobrine, Monster target) {
        if (!herobrine.isAlive() || target == null || !target.isAlive()) {
            return false;
        }

        if (herobrine.getStage() == HerobrineStage.STAGE_3) {
            return false;
        }

        return herobrine.doHurtTarget((ServerLevel) herobrine.level(), target);
    }

    public static boolean approach(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        double speed = herobrine.getStage() == HerobrineStage.STAGE_3 ? 1.0D : 0.85D;
        return herobrine.getNavigation().moveTo(target, speed);
    }

    public static boolean follow(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        double distance = herobrine.distanceTo(target);
        if (distance < 4.0D) {
            herobrine.getNavigation().stop();
            return true;
        }

        double speed = herobrine.getStage() == HerobrineStage.STAGE_3 ? 0.95D : 0.75D;
        return herobrine.getNavigation().moveTo(target, speed);
    }

    public static boolean hide(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        Vec3 away = herobrine.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 destination = herobrine.position().add(away.normalize().scale(10.0D));
        herobrine.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.8D);
        return true;
    }

    public static boolean retreat(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        Vec3 away = herobrine.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 destination = herobrine.position().add(away.normalize().scale(14.0D));
        herobrine.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
        return true;
    }

    public static boolean teleportBehind(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        ServerLevel level = (ServerLevel) herobrine.level();
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.001D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        double distance = 20.0D + level.getRandom().nextDouble() * 10.0D;
        double x = target.getX() - horizontal.x * distance;
        double z = target.getZ() - horizontal.z * distance;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                blockX,
                blockZ
        );

        BlockPos position = new BlockPos(blockX, groundY, blockZ);
        if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
            position = position.above();
        }

        herobrine.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        herobrine.getNavigation().stop();
        return true;
    }

    public static boolean triggerEncounter(HerobrineEntity herobrine, ServerPlayer target) {
        if (!isValidMovementTarget(herobrine, target)) {
            return false;
        }

        return teleportBehind(herobrine, target);
    }

    private static boolean isValidMovementTarget(HerobrineEntity herobrine, ServerPlayer target) {
        return herobrine.isAlive()
                && target != null
                && target.isAlive()
                && herobrine.level() == target.level();
    }

    public static boolean attackPlayer(HerobrineEntity herobrine, ServerPlayer target) {
        if (!herobrine.isAlive() || target == null || !target.isAlive()) {
            return false;
        }

        if (herobrine.getStage() != HerobrineStage.STAGE_3) {
            return false;
        }

        herobrine.setStage3Target(target);
        return true;
    }

    public static boolean stealFromChest(HerobrineEntity herobrine, ServerLevel level, BlockPos pos) {
        if (herobrine.getStage() != HerobrineStage.STAGE_2) {
            return false;
        }

        if (!(level.getBlockState(pos).getBlock() instanceof ChestBlock)) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return false;
        }

        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                occupied.add(slot);
            }
        }

        if (occupied.isEmpty() || !herobrine.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            return false;
        }

        int slot = occupied.get(herobrine.getRandom().nextInt(occupied.size()));
        ItemStack stolen = container.removeItem(slot, Math.min(MAX_STOLEN_ITEMS, container.getItem(slot).getCount()));
        if (stolen.isEmpty()) {
            return false;
        }

        herobrine.setItemSlot(EquipmentSlot.MAINHAND, stolen);
        return true;
    }

    public static boolean burnDrop(HerobrineEntity herobrine, ItemEntity itemEntity) {
        if (herobrine.getStage() != HerobrineStage.STAGE_2
                || itemEntity == null
                || !itemEntity.isAlive()) {
            return false;
        }

        itemEntity.setRemainingFireTicks(60);
        return true;
    }

    public static boolean breakBlock(HerobrineEntity herobrine, ServerLevel level, BlockPos pos) {
        if (herobrine.getStage() != HerobrineStage.STAGE_2) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeavesBlock)
                || (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) {
            return false;
        }

        return level.destroyBlock(pos, true, herobrine, 3);
    }

    private static boolean canAssist(HerobrineEntity herobrine) {
        return herobrine.isAlive()
                && herobrine.getStage() != HerobrineStage.STAGE_3;
    }

    private static boolean isAllowedGift(Item item) {
        return item == Items.TORCH
                || item == Items.GOLDEN_APPLE
                || item == Items.COOKED_BEEF
                || item == Items.STONE_PICKAXE
                || item == Items.STONE_AXE
                || item == Items.STONE_SHOVEL
                || item == Items.STONE_HOE
                || item == Items.LEATHER_HELMET
                || item == Items.LEATHER_CHESTPLATE
                || item == Items.LEATHER_LEGGINGS
                || item == Items.LEATHER_BOOTS;
    }

    private static boolean giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        return true;
    }
}
