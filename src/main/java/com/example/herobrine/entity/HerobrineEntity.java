package com.example.herobrine.entity;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineStage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HerobrineEntity extends PathfinderMob {
    private static final double PLAYER_INTERACTION_RANGE = 100.0D;
    private static final double STAGE3_ACTIVE_RANGE = 50.0D;
    private static final double STAGE3_SPEED = 0.40D;
    private static final double STAGE3_MAX_HEALTH = 1_000_000.0D;
    private static final double NORMAL_MAX_HEALTH = 20.0D;

    private HerobrineStage stage = HerobrineStage.STAGE_1;
    private long lifecycleStartDay = -1L;
    private long stage2Day = -1L;
    private long stage3Day = -1L;
    private int retreatTicks = 0;

    public HerobrineEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public HerobrineStage getStage() {
        return stage;
    }

    public void setStage(HerobrineStage newStage) {
        if (newStage == null || newStage == stage) {
            return;
        }

        HerobrineStage previousStage = stage;
        stage = newStage;
        applyStageAttributes();

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
    public Component getName() {
        return Component.literal(stage == HerobrineStage.STAGE_3 ? "Herobrine" : "Hero");
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

        applyStageAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Monster>(
                this,
                Monster.class,
                10,
                true,
                false,
                (target, level) -> stage != HerobrineStage.STAGE_3
        ));

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(
                this,
                Player.class,
                10,
                true,
                false,
                (target, level) -> stage == HerobrineStage.STAGE_3
        ));

        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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
        tickStageBehavior();
    }

    private void advanceScheduledStages(long currentDay) {
        if (stage == HerobrineStage.STAGE_1 && stage2Day >= 0L && currentDay >= stage2Day) {
            setStage(HerobrineStage.STAGE_2);
            HerobrineMod.LOGGER.info(
                    "Herobrine automatically entered Stage 2 on day {}",
                    currentDay
            );
        }
    }

    private void tickStageBehavior() {
        if (stage == HerobrineStage.STAGE_3) {
            tickStage3Behavior();
            return;
        }

        if (level().isNight()) {
            tickNightApproach();
        }
    }

    private void tickNightApproach() {
        if (tickCount % 20 != 0) {
            return;
        }

        Player nearest = findNearestPlayer(PLAYER_INTERACTION_RANGE);
        if (nearest == null) {
            return;
        }

        double distance = distanceTo(nearest);
        if (distance < 7.0D) {
            getNavigation().stop();
            return;
        }

        double speed = stage == HerobrineStage.STAGE_1 ? 0.55D : 0.65D;
        getNavigation().moveTo(nearest, speed);
    }

    private void tickStage3Behavior() {
        Player target = getTarget() instanceof Player player ? player : null;
        if (target == null || !target.isAlive() || distanceTo(target) > STAGE3_ACTIVE_RANGE) {
            setTarget(null);
            discard();
            return;
        }

        if (retreatTicks > 0) {
            retreatTicks--;
            getNavigation().stop();
            Vec3 away = position().subtract(target.position());
            if (away.lengthSqr() > 0.001D) {
                setDeltaMovement(away.normalize().scale(0.24D));
            }
            return;
        }

        // Stage 3 can move vertically toward the player instead of being confined
        // to normal ground navigation. The server remains authoritative.
        if (!onGround() && distanceTo(target) > 2.5D) {
            Vec3 towardTarget = target.getEyePosition().subtract(getEyePosition());
            if (towardTarget.lengthSqr() > 0.001D) {
                setDeltaMovement(towardTarget.normalize().scale(STAGE3_SPEED));
            }
        }
    }

    private Player findNearestPlayer(double range) {
        double rangeSqr = range * range;
        Player nearest = null;
        double nearestDistanceSqr = rangeSqr;

        for (Player player : level().players()) {
            if (!player.isAlive()) {
                continue;
            }

            double distanceSqr = distanceToSqr(player);
            if (distanceSqr <= nearestDistanceSqr) {
                nearest = player;
                nearestDistanceSqr = distanceSqr;
            }
        }

        return nearest;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hurt = super.doHurtTarget(target);

        if (hurt && stage == HerobrineStage.STAGE_3 && target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 140));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140));
            retreatTicks = 30;
        }

        return hurt;
    }

    @Override
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

    private void applyStageAttributes() {
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance movementSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance knockbackResistance = getAttribute(Attributes.KNOCKBACK_RESISTANCE);

        if (maxHealth == null || movementSpeed == null || attackDamage == null || knockbackResistance == null) {
            return;
        }

        if (stage == HerobrineStage.STAGE_3) {
            maxHealth.setBaseValue(STAGE3_MAX_HEALTH);
            movementSpeed.setBaseValue(STAGE3_SPEED);
            attackDamage.setBaseValue(8.0D);
            knockbackResistance.setBaseValue(1.0D);
            setNoGravity(true);
            setHealth((float) STAGE3_MAX_HEALTH);
        } else {
            maxHealth.setBaseValue(NORMAL_MAX_HEALTH);
            movementSpeed.setBaseValue(stage == HerobrineStage.STAGE_2 ? 0.28D : 0.25D);
            attackDamage.setBaseValue(stage == HerobrineStage.STAGE_2 ? 4.0D : 3.0D);
            knockbackResistance.setBaseValue(0.0D);
            setNoGravity(false);
            setHealth(Math.min(getHealth(), (float) NORMAL_MAX_HEALTH));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, NORMAL_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }
}
