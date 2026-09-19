package com.example.herobrine.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MashaalItem extends Item {
    private static final int PARTICLE_INTERVAL = 4;

    public MashaalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            net.minecraft.world.entity.Entity entity,
            int slot,
            boolean selected
    ) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (!selected || !(entity instanceof Player player) || level.isClientSide()) {
            return;
        }

        if (level.getGameTime() % PARTICLE_INTERVAL == 0L) {
            level.addParticle(
                    ParticleTypes.FLAME,
                    player.getX(),
                    player.getEyeY() - 0.18D,
                    player.getZ(),
                    0.0D,
                    0.025D,
                    0.0D
            );
        }
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (!level.isClientSide()) {
            level.playSound(
                    null,
                    user.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.45F,
                    0.9F + level.getRandom().nextFloat() * 0.2F
            );
        }

        return InteractionResult.SUCCESS;
    }
}
