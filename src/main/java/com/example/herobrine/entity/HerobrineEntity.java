package com.example.herobrine.entity;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineStage;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class HerobrineEntity extends PathfinderMob {
    private HerobrineStage stage = HerobrineStage.STAGE_1;
    private long lifecycleStartDay = -1L;
    private long stage2Day = -1L;
    private long stage3Day = -1L;

    public HerobrineEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public HerobrineStage getStage() {
        return stage;
    }

    public void setStage(HerobrineStage stage) {
        this.stage = stage;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putLong("LifecycleStartDay", lifecycleStartDay);
        valueOutput.putLong("Stage2Day", stage2Day);
        valueOutput.putLong("Stage3Day", stage3Day);
        valueOutput.putString("HerobrineStage", stage.name());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        lifecycleStartDay = valueInput.getLong("LifecycleStartDay").orElse(-1L);
        stage2Day = valueInput.getLong("Stage2Day").orElse(-1L);
        stage3Day = valueInput.getLong("Stage3Day").orElse(-1L);
        String savedStage = valueInput.getString("HerobrineStage").orElse(HerobrineStage.STAGE_1.name());
        try {
            stage = HerobrineStage.valueOf(savedStage);
        } catch (IllegalArgumentException ignored) {
            stage = HerobrineStage.STAGE_1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && lifecycleStartDay < 0L) {
            long currentDay = level().getGameTime() / 24000L;
            lifecycleStartDay = currentDay;
            stage2Day = currentDay + 10L + getRandom().nextInt(6);
            HerobrineMod.LOGGER.info("Herobrine Stage 1 lifecycle started: Stage 2 scheduled for day {}", stage2Day);
        }
    }

    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
        if (stage == HerobrineStage.STAGE_3) {
            float safeAmount = Math.min(amount, Math.max(0.0F, getHealth() - 1.0F));
            boolean damaged = super.hurtServer(level, source, safeAmount);
            setHealth(getMaxHealth());
            return damaged;
        }
        return super.hurtServer(level, source, amount);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }
}
