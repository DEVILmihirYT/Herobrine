package com.example.herobrine.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MashaalItem extends Item {
    public MashaalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (!level.isClientSide()) {
            Vec3 flamePos = user.getEyePosition().add(user.getLookAngle().scale(0.55D));

            for (int i = 0; i < 3; i++) {
                level.addParticle(
                        ParticleTypes.FLAME,
                        flamePos.x,
                        flamePos.y + i * 0.05D,
                        flamePos.z,
                        0.0D,
                        0.025D,
                        0.0D
                );
            }

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
