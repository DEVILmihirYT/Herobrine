package com.example.herobrine.entity;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineStage;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * Changes the entity's stage on the server.
     * Stage 3 is intentionally not triggered automatically yet; later systems
     * will decide when a Stage 3 encounter is actually justified.
     */
    public void setStage(HerobrineStage newStage) {
        if (newStage == null || newStage == stage) {
            return;
        }

        HerobrineStage previousStage = stage;
        stage = newStage;

        if (!level().isClientSide()) {
            HerobrineMod.LOGGER.info(
                    "Herobrine stage changed from {} to {} at {}",
                    previousStage,
                    newStage,
                    blockPosition()
            );
        }
    }

    public long getLifecycleStartDay() {
        return lifecycleStartDay;
    }

    public long getStage2Day() {
        return stage2Day;
    }

    public long getStage3Day() {
        return stage3Day;
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

        String savedStage = valueInput
                .getString("HerobrineStage")
                .orElse(HerobrineStage.STAGE_1.name());

        try {
            stage = HerobrineStage.valueOf(savedStage);
        } catch (IllegalArgumentException ignored) {
            stage = HerobrineStage.STAGE_1;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        long currentDay = level().getGameTime() / 24000L;

        if (lifecycleStartDay < 0L) {
            lifecycleStartDay = currentDay;
            stage2Day = currentDay + 10L + getRandom().nextInt(6);

            HerobrineMod.LOGGER.info(
                    "Herobrine Stage 1 lifecycle started: Stage 2 scheduled for day {}",
                    stage2Day
            );
        }

        advanceScheduledStages(currentDay);
    }

    /**
     * Applies only the currently safe automatic lifecycle transition:
     * Stage 1 -> Stage 2 when the scheduled day is reached.
     *
     * Stage 3 remains event-driven and will be controlled later by the
     * grudge/revenge and encounter systems.
     */
    private void advanceScheduledStages(long currentDay) {
        if (stage == HerobrineStage.STAGE_1 && stage2Day >= 0L && currentDay >= stage2Day) {
            setStage(HerobrineStage.STAGE_2);
            HerobrineMod.LOGGER.info(
                    "Herobrine automatically entered Stage 2 on day {}",
                    currentDay
            );
        }
    }

    public boolean hurtServer(ServerLevel level,
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
