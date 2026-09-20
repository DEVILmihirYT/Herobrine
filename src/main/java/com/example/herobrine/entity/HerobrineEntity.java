package com.example.herobrine.entity;

import com.example.HerobrineMod;
import com.example.herobrine.HerobrineStage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HerobrineEntity extends PathfinderMob {
    private static final double STAGE3_ACTIVE_RANGE = 50.0D;
    private static final double STAGE3_SPEED = 0.40D;
    private static final double STAGE3_MAX_HEALTH = 1_000_000.0D;
    private static final double NORMAL_MAX_HEALTH = 20.0D;

    private HerobrineStage stage = HerobrineStage.STAGE_1;
    private long lifecycleStartDay = -1L;
    private long stage2Day = -1L;
    private long stage3Day = -1L;
    private int retreatTicks = 0;
    private int stage3AttackCooldownTicks = 0;
    private int stage1SpawnAnimationTicks = 0;
    private double stage1SpawnBaseY = 0.0D;
    private final Set<UUID> interactionBlockedPlayers = new HashSet<>();

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
        interactionBlockedPlayers.clear();
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

    public void markStage3Activated(long day) {
        stage3Day = day;
    }

    public boolean isInteractionBlocked(UUID playerId) {
        return interactionBlockedPlayers.contains(playerId);
    }

    /**
     * Enforces the staged Creative/Operator rules on the server.
     * Stage 3 always removes operator status and Creative mode.
     * Stage 1/2 suppress interaction with players that are Creative or Operator.
     */
    public void enforcePlayerRestrictions(ServerPlayer player) {
        if (stage == HerobrineStage.STAGE_3) {
            boolean changed = false;

            if (player.gameMode() == GameType.CREATIVE) {
                player.setGameMode(GameType.SURVIVAL);
                changed = true;
            }

            if (player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
                player.level().getServer().getPlayerList().deop(player.nameAndId());
                changed = true;
            }

            if (changed) {
                player.sendSystemMessage(Component.literal(
                        "Herobrine: Creative/Operator privileges are not allowed during Stage 3."
                ));
            }
            interactionBlockedPlayers.remove(player.getUUID());
            return;
        }

        boolean restricted = player.gameMode() == GameType.CREATIVE
                || player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);

        if (restricted) {
            if (interactionBlockedPlayers.add(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        player.getName().getString()
                                + " did create/operator, am shutting down for you."
                ));
            }
            return;
        }

        if (interactionBlockedPlayers.remove(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "Hero: You are back to Member + Survival. I am active again."
            ));
        }
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

        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        if (stage1SpawnAnimationTicks > 0) {
            tickStage1SpawnAnimation();
            return;
        }

        tickStageBehavior();
    }

    public void beginStage1SpawnAnimation(BlockPos blockPos) {
        if (stage != HerobrineStage.STAGE_1 || level().isClientSide()) {
            return;
        }

        stage1SpawnAnimationTicks = 36;
        stage1SpawnBaseY = blockPos.getY() - 1.35D;
        setNoGravity(true);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setPos(blockPos.getX() + 0.5D, stage1SpawnBaseY, blockPos.getZ() + 0.5D);
    }

    private void tickStage1SpawnAnimation() {
        if (stage != HerobrineStage.STAGE_1) {
            stage1SpawnAnimationTicks = 0;
            setNoGravity(false);
            return;
        }

        float progress = 1.0F - (stage1SpawnAnimationTicks / 36.0F);
        double y = stage1SpawnBaseY + 1.35D * progress;
        setPos(getX(), y, getZ());
        setDeltaMovement(Vec3.ZERO);
        setTarget(null);
        getNavigation().stop();

        stage1SpawnAnimationTicks--;
        if (stage1SpawnAnimationTicks <= 0) {
            setPos(getX(), stage1SpawnBaseY + 1.35D, getZ());
            setNoGravity(false);
        }
    }

    private void tickStageBehavior() {
        if (stage == HerobrineStage.STAGE_3) {
            tickStage3Behavior();
        }
    }

    private void tickStage3Behavior() {
        Player target = getTarget() instanceof Player player ? player : null;

        if (stage3AttackCooldownTicks > 0) {
            setTarget(null);
            getNavigation().stop();

            if (target != null) {
                Vec3 away = position().subtract(target.position());
                if (away.lengthSqr() > 0.001D) {
                    setDeltaMovement(away.normalize().scale(0.24D));
                }
            }

            return;
        }

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

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        boolean hurt = super.doHurtTarget(level, target);

        if (hurt && stage == HerobrineStage.STAGE_3 && target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 140));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 140));
            stage3AttackCooldownTicks = 140;
            retreatTicks = 40;
            setTarget(null);
            getNavigation().stop();
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
