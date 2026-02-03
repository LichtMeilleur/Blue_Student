package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import java.util.UUID;

public class HinaEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(HinaEntity.class, StudentAiMode.TRACKED);

    public static final String ANIM_FLY = "animation.model.fly";
    public static final String ANIM_FLY_SHOT = "animation.model.fly_shot";

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(ANIM_FLY);
    private static final RawAnimation FLY_SHOT = RawAnimation.begin().thenPlay(ANIM_FLY_SHOT);

    // ===== Hina Fly Skill Params =====
    private static final int FLY_DURATION_TICKS  = 20 * 20;  // 20秒
    private static final int FLY_COOLDOWN_TICKS  = 20 * 12;  // 12秒
    private static final double HOVER_MIN_Y_VEL  = 0.03;     // 滞空維持の下限
    private static final double LAND_DESCEND_SPEED = 0.08;   // 着地フェーズ下降速度
    private static final int LANDING_MAX_TICKS = 20 * 6;     // 着地フェーズ最大6秒（保険）

    // ===== Fly State (server only timers) =====
    private int flyActiveTicks = 0;
    private int flyCooldownTicks = 0;
    private int landingTicksLeft = 0;

    // ===== Tracked flags =====
    private static final TrackedData<Boolean> FLYING_T =
            DataTracker.registerData(HinaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> LANDING_T =
            DataTracker.registerData(HinaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> FLY_SHOOT_T =
            DataTracker.registerData(HinaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public boolean isFlying() { return this.dataTracker.get(FLYING_T); }
    public boolean isFlyLanding() { return this.dataTracker.get(LANDING_T); }
    public boolean isFlyShooting() { return this.dataTracker.get(FLY_SHOOT_T); }
    public void setFlyShooting(boolean v) { this.dataTracker.set(FLY_SHOOT_T, v); }

    private static final UUID FLY_SPEED_UUID =
            UUID.fromString("f0b8c8a4-2c1f-4c9a-8a3a-6d7c2f3c1b11");


    public HinaEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    public StudentId getStudentId() {
        return StudentId.HINA;
    }

    @Override
    protected TrackedData<StudentAiMode> getAiModeTrackedData() {
        return AI_MODE;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        ItemStack inHand = player.getStackInHand(hand);

        // スニーク素手：ベッドリンク（既存）
        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.HINA);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "hina"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        // fly_shot アニメ用スイッチ
        this.goalSelector.add(2, new HinaFlyGoal(this, this));

        this.goalSelector.add(3, new StudentAimFireGoal(this, this));
        this.goalSelector.add(4, new StudentStuckEscapeGoal(this, this));
        this.goalSelector.add(5, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(6,
                new StudentReturnToOwnerGoal(this, this,
                        1.35, 28.0, 2.5, 48.0, 20
                ));

        this.goalSelector.add(7, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(
                this, HostileEntity.class, 8.0f, 1.0, 1.35
        ));

        this.goalSelector.add(8, new StudentCombatGoal(this, this));
        this.goalSelector.add(9, new StudentEvadeGoal(this, this));

        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return HinaEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { HinaEntity.this.setSecurityPos(pos); }
                },
                1.0
        ));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

    @Override
    public void tick() {
        super.tick();

        // ★サーバーだけ進行
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            tickFlySkill(sw);
        }

    }

    // ===== Fly Skill State Machine =====
    private void tickFlySkill(ServerWorld sw) {
        // 復活ロック中は即解除
        if (this.isLifeLockedForGoal()) {
            stopFlyImmediately();
            flyActiveTicks = 0;
            flyCooldownTicks = 0;
            landingTicksLeft = 0;
            return;
        }

        // CT減算
        if (flyCooldownTicks > 0) flyCooldownTicks--;

        // ---- Active (滞空中) ----
        if (flyActiveTicks > 0) {
            flyActiveTicks--;

            // 初回だけ開始
            if (!isFlying()) startFlyInternal();

            // 滞空維持
            keepFlyingPhysics();

            // 時間切れ → Landing
            if (flyActiveTicks == 0) {
                startLandingInternal();
            }
            return;
        }

        // ---- Landing (ゆっくり降りる) ----
        if (isFlyLanding()) {
            tickLandingInternal();
            return;
        }

        // ---- 非発動中：条件を満たせば発動 ----
        if (flyCooldownTicks <= 0) {
            boolean danger = hasNearbyEnemy(sw) || hasIncomingProjectile(sw);
            if (danger) {
                startFlySkill();
            }
        }
    }

    private void startFlySkill() {
        flyActiveTicks = FLY_DURATION_TICKS;
        // 発動中はCT開始しない（着地後に開始）
        startFlyInternal();
    }

    private void startFlyInternal() {
        this.dataTracker.set(FLYING_T, true);
        this.dataTracker.set(LANDING_T, false);

        this.setNoGravity(true);
        this.fallDistance = 0.0f;

        // ★接地判定を切るため、必ず少し上へ
        this.refreshPositionAndAngles(
                this.getX(),
                this.getY() + 0.12,
                this.getZ(),
                this.getYaw(),
                this.getPitch()
        );

        // ★上昇を強制（ジャンプ相当）
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x, Math.max(v.y, 0.42), v.z);
        this.velocityDirty = true;

        this.getNavigation().stop();
        applyFlySpeed(true);
    }

    private void keepFlyingPhysics() {
        this.fallDistance = 0.0f;

        // 接地してたら再度ちょい上げ（干渉対策）
        if (this.isOnGround()) {
            this.refreshPositionAndAngles(
                    this.getX(),
                    this.getY() + 0.10,
                    this.getZ(),
                    this.getYaw(),
                    this.getPitch()
            );
        }

        Vec3d v = this.getVelocity();

        // 落下し始めたら支える（ホバー）
        if (v.y < HOVER_MIN_Y_VEL) {
            this.setVelocity(v.x, HOVER_MIN_Y_VEL, v.z);
            this.velocityDirty = true;
        }
    }

    private void startLandingInternal() {
        this.dataTracker.set(FLYING_T, true);   // 飛行状態は維持（アニメも維持）
        this.dataTracker.set(LANDING_T, true);

        landingTicksLeft = LANDING_MAX_TICKS;

        this.setNoGravity(true);
        this.fallDistance = 0.0f;
    }

    private void tickLandingInternal() {
        this.fallDistance = 0.0f;

        // 保険：長すぎたら強制終了
        if (landingTicksLeft > 0) landingTicksLeft--;
        else {
            stopFlyAfterLanded();
            return;
        }

        // 着地したら終了
        if (this.isOnGround()) {
            stopFlyAfterLanded();
            return;
        }

        // ゆっくり下降（水平維持）
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x, -LAND_DESCEND_SPEED, v.z);
        this.velocityDirty = true;
    }

    private void stopFlyAfterLanded() {
        this.dataTracker.set(FLYING_T, false);
        this.dataTracker.set(LANDING_T, false);
        this.dataTracker.set(FLY_SHOOT_T, false);

        this.setNoGravity(false);
        this.fallDistance = 0.0f;

        flyCooldownTicks = FLY_COOLDOWN_TICKS; // ★着地後にCT開始
        landingTicksLeft = 0;
        applyFlySpeed(false);
    }

    private void stopFlyImmediately() {
        this.dataTracker.set(FLYING_T, false);
        this.dataTracker.set(LANDING_T, false);
        this.dataTracker.set(FLY_SHOOT_T, false);

        this.setNoGravity(false);
        this.fallDistance = 0.0f;
        landingTicksLeft = 0;
        applyFlySpeed(false);
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (isFlying()) {
            if (isFlyShooting()) return FLY_SHOT;
            return FLY;
        }
        return null;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        if (this.isFlying() || this.isFlyLanding()) return false;
        return super.handleFallDamage(fallDistance, damageMultiplier, source);
    }

    // ===== danger detection =====
    private boolean hasNearbyEnemy(ServerWorld sw) {
        var box = this.getBoundingBox().expand(2.0);
        return !sw.getEntitiesByClass(
                net.minecraft.entity.mob.HostileEntity.class,
                box,
                e -> e.isAlive()
        ).isEmpty();
    }

    private boolean hasIncomingProjectile(ServerWorld sw) {
        var box = this.getBoundingBox().expand(8.0);
        var myPos = this.getEyePos();

        var list = sw.getEntitiesByClass(
                net.minecraft.entity.projectile.ProjectileEntity.class,
                box,
                p -> p.isAlive()
        );

        for (var p : list) {
            var v = p.getVelocity();
            if (v.lengthSquared() < 0.01) continue;

            var toMe = myPos.subtract(p.getPos());
            if (toMe.lengthSquared() < 0.01) continue;

            double dot = v.normalize().dotProduct(toMe.normalize());
            if (dot > 0.85) return true;
        }
        return false;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FLYING_T, false);
        this.dataTracker.startTracking(LANDING_T, false);
        this.dataTracker.startTracking(FLY_SHOOT_T, false);
    }

    private void applyFlySpeed(boolean on) {
        var ms = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (ms == null) return;

        ms.removeModifier(FLY_SPEED_UUID);
        if (on) {
            ms.addPersistentModifier(new EntityAttributeModifier(
                    FLY_SPEED_UUID, "hina_fly_speed", 0.35, // +35%（好みで0.2〜0.6）
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }
}
