package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
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
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.control.FlightMoveControl;


public class HinaEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(HinaEntity.class, StudentAiMode.TRACKED);

    public static final String ANIM_FLY = "animation.model.fly";
    public static final String ANIM_FLY_SHOT = "animation.model.fly_shot";

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(ANIM_FLY);
    private static final RawAnimation FLY_SHOT = RawAnimation.begin().thenLoop(ANIM_FLY_SHOT);


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
    private int flyShotPulseTicks = 0;
    // スキル終了後の落下無効猶予
    private int noFallTicks = 0;
    private static final int NO_FALL_GRACE_TICKS = 20 * 10; // 10秒ぶん“有効期間”
    private boolean wasOnGround = true;


    private static final UUID FLY_SPEED_UUID =
            UUID.fromString("f0b8c8a4-2c1f-4c9a-8a3a-6d7c2f3c1b11");


    public HinaEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
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

    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        this.goalSelector.add(2, new StudentStuckEscapeGoal(this, this));
        this.goalSelector.add(3, new EscapeDangerGoal(this, 1.25));

// ★飛行の位置取り（MOVE）
       // this.goalSelector.add(4, new HinaStrafeFlyGoal(this, this));
        this.goalSelector.add(4, new HinaAirCombatGoal(this, this));
// ★Combat（射撃キュー＆リロード）※飛行中は移動しないよう修正した前提
        this.goalSelector.add(5, new StudentCombatGoal(this, this));

// ★Aim（LOOK＋実射撃）
        this.goalSelector.add(6, new StudentAimGoal(this, this));

// ★演出フラグ（あってもなくても）
// ※今のHinaFlyGoalは hasQueuedFire が条件なので、入れても害は少ない
        this.goalSelector.add(7, new HinaFlyGoal(this, this));

        this.goalSelector.add(9, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));
        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));

        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return HinaEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { HinaEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return AbstractStudentEntity.createAttributes()
                // 既に movement_speed 等があるならそのままでOK
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.8); // ★ここが必須
    }


    @Override
    public void tick() {
        super.tick();

        // ★サーバーだけ進行
        if (!this.getWorld().isClient && noFallTicks > 0) {
            noFallTicks--;
        }

        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            tickFlySkill(sw);
        }
        // fly_shot パルスの減算（サーバーのみでOK）
        if (!this.getWorld().isClient) {
            if (flyShotPulseTicks > 0) {
                flyShotPulseTicks--;
                if (flyShotPulseTicks == 0) {
                    this.dataTracker.set(FLY_SHOOT_T, false);
                }
            }
        }
        if (!this.getWorld().isClient) {
            if (noFallTicks > 0) noFallTicks--;
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
            boolean danger =
                    hasNearbyEnemy(sw)
                            || hasIncomingProjectile(sw)
                            || this.hurtTime > 0;  // ★最近ダメージ受けたら飛ぶ

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
        noFallTicks = NO_FALL_GRACE_TICKS;

    }

    private void stopFlyImmediately() {
        this.dataTracker.set(FLYING_T, false);
        this.dataTracker.set(LANDING_T, false);
        this.dataTracker.set(FLY_SHOOT_T, false);

        this.setNoGravity(false);
        this.fallDistance = 0.0f;
        landingTicksLeft = 0;
        applyFlySpeed(false);
        noFallTicks = NO_FALL_GRACE_TICKS;

    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (isFlying()) {
            // ★「撃つ予定(キュー)」ではなく「撃った演出(shot ticks)」で判定する
            if (this.getClientShotTicksForAnim() > 0) return FLY_SHOT;
            return FLY;
        }
        return null;
    }





    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {

        boolean blocked = this.isFlying() || this.isFlyLanding() || noFallTicks > 0;

       /* System.out.println(
                "[HinaFall] fd=" + fallDistance +
                        " flying=" + isFlying() +
                        " landing=" + isFlyLanding() +
                        " noFall=" + noFallTicks +
                        " blocked=" + blocked+
                " active=" + flyActiveTicks +
                        " cd=" + flyCooldownTicks +
                        " landingLeft=" + landingTicksLeft
        );*/

        if (blocked) return false;
        return super.handleFallDamage(fallDistance, damageMultiplier, source);
    }




    // ===== danger detection =====
    private boolean hasNearbyEnemy(ServerWorld sw) {
        var box = this.getBoundingBox().expand(8.0);
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
                    FLY_SPEED_UUID, "hina_fly_speed", 0.6, // +35%（好みで0.2〜0.6）
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    // ===== fly_shot を短時間だけONにする（AimFireGoalから呼ぶ）=====
    public void beginFlyShotPulse(int ticks) {
        // 飛行してないなら何もしない
        if (!isFlying()) return;

        // いったんON
        this.dataTracker.set(FLY_SHOOT_T, true);

        // ticks後にOFFへ戻す（サーバー側で管理したいので age を使っても良いが、最小なら tickで消す）
        // ここでは簡易：フィールドでカウントする
        this.flyShotPulseTicks = Math.max(this.flyShotPulseTicks, ticks);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (this.getWorld().isClient) return;

        if (isFlying() || isFlyLanding()) {
            this.setNoGravity(true);
            this.fallDistance = 0;

            // ★暴走対策：速度を上限でクランプ
            Vec3d v = this.getVelocity();

            double maxH = 0.9;  // 水平最大（まず 0.6〜1.2）
            double maxYUp = 0.45;
            double maxYDown = -0.60;

            double hxz = Math.sqrt(v.x * v.x + v.z * v.z);
            if (hxz > maxH) {
                double s = maxH / hxz;
                v = new Vec3d(v.x * s, v.y, v.z * s);
            }

            if (v.y > maxYUp) v = new Vec3d(v.x, maxYUp, v.z);
            if (v.y < maxYDown) v = new Vec3d(v.x, maxYDown, v.z);

            this.setVelocity(v);
            this.velocityDirty = true;

        } else {
            this.setNoGravity(false);
        }
        // ★ super.tickMovement() より前にやる
        boolean air = this.isFlying() || this.isFlyLanding();

        if (air) {
            this.setNoGravity(true);
            this.fallDistance = 0.0f;
        }

        super.tickMovement();

        // ★念のため、飛行/着地中は毎tick 0 にしておく
        if (air) {
            this.fallDistance = 0.0f;
        } else {
            this.setNoGravity(false);
        }

        Vec3d v = getVelocity();
        double sp = v.length();
        if (sp > 2.0) {
            System.out.println("[HinaFlySpike] sp=" + sp + " v=" + v +
                    " flyingSpeed=" + getAttributeValue(EntityAttributes.GENERIC_FLYING_SPEED) +
                    " moveSpeed=" + getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        }

    }


}
