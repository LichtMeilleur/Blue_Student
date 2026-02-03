package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;

public class HoshinoEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(HoshinoEntity.class, StudentAiMode.TRACKED);
    private static final TrackedData<Boolean> TD_GUARDING =
            DataTracker.registerData(HoshinoEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> TD_GUARD_SHOOTING =
            DataTracker.registerData(HoshinoEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final String ANIM_GUARD_IDLE = "animation.model.guard_idle";
    public static final String ANIM_GUARD_WALK = "animation.model.guard_walk";
    public static final String ANIM_GUARD_SHOT = "animation.model.guard_shot";

    private static final RawAnimation GUARD_IDLE = RawAnimation.begin().thenLoop(ANIM_GUARD_IDLE);
    private static final RawAnimation GUARD_WALK = RawAnimation.begin().thenLoop(ANIM_GUARD_WALK);
    private static final RawAnimation GUARD_SHOT = RawAnimation.begin().thenPlay(ANIM_GUARD_SHOT);

    // ===== Guard Skill Params =====
    private static final int GUARD_DURATION_TICKS = 60;   // 3秒（20t=1秒）
    private static final int GUARD_COOLDOWN_TICKS = 100;  // 5秒
    private static final int TAUNT_INTERVAL_TICKS = 10;   // タウント更新間隔
    private static final double TAUNT_RADIUS = 12.0;

    // ===== Guard State =====
    private int guardActiveTicks = 0;
    private int guardCooldownTicks = 0;

    private static final int GUARD_DURATION = 20 * 6;   // 6秒
    private static final int GUARD_COOLDOWN = 20 * 12;

    private boolean guarding = false;          // “見た目/挙動”用フラグ（実体はguardActiveTicks>0）
    private boolean guardShooting = false;     // ガード撃ちアニメ用



    // isGuardShooting() / setGuardShooting() を作る（←質問の答え）
    public boolean isGuarding() { return this.dataTracker.get(TD_GUARDING); }
    public boolean isGuardShooting() { return this.dataTracker.get(TD_GUARD_SHOOTING); }


    // 移動速度の “元値” をキャッシュ
    private double baseMoveSpeedCached = -1;


     // 12秒


    public HoshinoEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    public StudentId getStudentId() { return StudentId.HOSHINO; }

    @Override
    protected TrackedData<StudentAiMode> getAiModeTrackedData() { return AI_MODE; }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        var inHand = player.getStackInHand(hand);
        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.HOSHINO);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "hoshino"), false);
            return ActionResult.CONSUME;
        }
        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        // ★GuardGoalは「撃つ」ではなく「ガード射撃アニメのスイッチ」だけにするのが安全
        this.goalSelector.add(2, new HoshinoGuardGoal(this, this));

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
                    @Override public BlockPos getSecurityPos() { return HoshinoEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { HoshinoEntity.this.setSecurityPos(pos); }
                },
                1.0
        ));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            tickGuardSkill(sw);
        }
    }





    // ===== Guard Skill State Machine =====
    private void tickGuardSkill(ServerWorld sw) {
        // 復活ロック中は一切ガードしない
        if (this.isLifeLockedForGoal()) {
            if (guarding) setGuardingInternal(false);
            guardActiveTicks = 0;
            guardCooldownTicks = 0;
            return;
        }

        if (guardCooldownTicks > 0) guardCooldownTicks--;

        if (guardActiveTicks > 0) {
            guardActiveTicks--;

            // 発動中はタウント継続（敵のターゲットを定期更新）
            if (this.age % TAUNT_INTERVAL_TICKS == 0) {
                applyTaunt(sw);
            }

            if (!guarding) setGuardingInternal(true);

            // 終了処理
            if (guardActiveTicks == 0) {
                setGuardingInternal(false);
                guardCooldownTicks = GUARD_COOLDOWN_TICKS;
            }
            return;
        }

        // 非発動中：危険なら開始（ただしCDが0の時だけ）
        if (guardCooldownTicks <= 0) {
            boolean danger = hasNearbyEnemy(sw) || hasIncomingProjectile(sw);
            if (danger) {
                startGuardSkill(sw);
            }
        }
    }

    private void startGuardSkill(ServerWorld sw) {
        guardActiveTicks = GUARD_DURATION_TICKS;
        setGuardingInternal(true);

        // 開始時に強めタウント
        applyTaunt(sw);
    }

    // ===== 危険検知 =====
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
            Vec3d v = p.getVelocity();
            if (v.lengthSquared() < 0.01) continue;

            Vec3d toMe = myPos.subtract(p.getPos());
            if (toMe.lengthSquared() < 0.01) continue;

            double dot = v.normalize().dotProduct(toMe.normalize());
            if (dot > 0.85) return true;
        }
        return false;
    }

    // ===== タウント（ヘイト集め）=====
    private void applyTaunt(ServerWorld sw) {
        var box = this.getBoundingBox().expand(TAUNT_RADIUS);

        var mobs = sw.getEntitiesByClass(
                net.minecraft.entity.mob.HostileEntity.class,
                box,
                e -> e.isAlive()
        );

        for (var m : mobs) {
            // すでに別ターゲットでも上書きしてOK（タンクなので）
            m.setTarget(this);
        }
    }

    // ===== バフON/OFF + 移動減速 =====
    private void setGuardingInternal(boolean on) {
        if (!this.getWorld().isClient) this.dataTracker.set(TD_GUARDING, on);

        if (on) {
            applyGuardBuff(true, 8.0, 6.0, 6.0f);
            setMovementSpeedMultiplier(0.70);
        } else {
            applyGuardBuff(false, 0, 0, 0);
            setMovementSpeedMultiplier(1.00);
            if (!this.getWorld().isClient) this.dataTracker.set(TD_GUARD_SHOOTING, false);
        }
    }


    private void setMovementSpeedMultiplier(double mul) {
        var inst = getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) return;

        if (baseMoveSpeedCached < 0) {
            baseMoveSpeedCached = inst.getBaseValue();
            if (baseMoveSpeedCached <= 0) baseMoveSpeedCached = 0.35;
        }
        inst.setBaseValue(baseMoveSpeedCached * mul);
    }

    // ===== アニメ差し替え =====
    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (!isGuarding()) return null;
        if (isGuardShooting()) return GUARD_SHOT;

        boolean moving = this.getVelocity().horizontalLengthSquared() > 0.002;
        return moving ? GUARD_WALK : GUARD_IDLE;
    }


    public void setGuardShooting(boolean v) {
        if (!this.getWorld().isClient) this.dataTracker.set(TD_GUARD_SHOOTING, v);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TD_GUARDING, false);
        this.dataTracker.startTracking(TD_GUARD_SHOOTING, false);
    }

}
