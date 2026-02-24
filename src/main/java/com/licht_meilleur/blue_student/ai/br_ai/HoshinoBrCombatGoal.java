package com.licht_meilleur.blue_student.ai.br_ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentBrAction;
import com.licht_meilleur.blue_student.student.StudentForm;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;


import java.util.Comparator;
import java.util.EnumSet;

public class HoshinoBrCombatGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;
    private LivingEntity target;

    private static final double DETECT_EXTRA = 10.0;

    private static final double TACKLE_RANGE = 3.3;
    private static final double BASH_RANGE   = 3.9;

    private static final double SHOTGUN_MIN = 4.0;
    private static final double SHOTGUN_MAX = 8.5;

    private static final double EVADE_DIST = 2.2;
    private static final double APPROACH_IF_OVER = 10.0;

    private static final double SPEED_CHASE = 1.35;
    private static final double SPEED_AIM   = 1.15;

    private static final int REPATH_INTERVAL = 12;
    private int repathCooldown = 0;

    // ===== action hold =====
    private StudentBrAction current = StudentBrAction.NONE;
    private int actionHoldTicks = 0;
    private int actionAge = 0;

    // ===== cooldowns =====
    private int cdTackle = 0;
    private int cdBash   = 0;
    private int cdDodge  = 0;
    private int cdMain   = 0;
    private int cdSub    = 0;
    private int cdSide   = 0;

    // ===== SUB burst (3 shots) =====
    private int subBurstLeft = 0;
    private int subBurstCooldown = 0;
    private boolean subFlip = false; // A/B 切り替えでアニメ再生を強制

    // ===== single-shot scheduling =====
    private boolean shotQueuedThisAction = false;

    private int fireCooldown = 0;

    private boolean subToggle = false;
    private int subBurstCd = 0;

    private int sideDashTicksLeft = 0;
    private Vec3d sideDashVel = Vec3d.ZERO;

    private int lastHurtTime = 0;

    private boolean meleeHitDone = false;

    // ★アニメを流し切るまで割り込み禁止にするロック
    private int actionLockTicks = 0;
    private int hitReactCooldown = 0;



    public HoshinoBrCombatGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private boolean isBr() {
        return (student instanceof AbstractStudentEntity ase) && ase.getForm() == StudentForm.BR;
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (!isBr()) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        target = findTarget();
        if (target != null) mob.setTarget(target);
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (!isBr()) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        if (target == null || !target.isAlive()) return false;

        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        double keep = main.range + DETECT_EXTRA;
        return mob.squaredDistanceTo(target) <= keep * keep;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        clearCds();
        stopAction();
        mob.getNavigation().stop();

        subBurstLeft = 0;
        subBurstCooldown = 0;
        subFlip = false;
        shotQueuedThisAction = false;
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
        stopAction();
        clearCds();
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;



        tickCds();
        if (hitReactCooldown > 0) hitReactCooldown--;
        if (fireCooldown > 0) fireCooldown--;
        if (repathCooldown > 0) repathCooldown--;
        if (cdSide > 0) cdSide--;
        if (subBurstCooldown > 0) subBurstCooldown--;
        if (actionLockTicks > 0) actionLockTicks--;

        // target refresh
        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            return;
        }

        final double dist = mob.distanceTo(target);
        final boolean canSee = mob.getVisibilityCache().canSee(target);

        final WeaponSpec mainSpec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        final WeaponSpec subSpec  = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.SUB_L);


        // ===== 被弾リアクションは最優先で割り込む =====
        int ht = mob.hurtTime;
        boolean gotHitThisTick = (ht > lastHurtTime);
        lastHurtTime = ht;

        boolean ignoreHitReact = (mob instanceof AbstractStudentEntity ase) && ase.shouldIgnoreHitReactNow();//赤文字
// ★ロック中は被弾割り込みしない
        if (gotHitThisTick && !ignoreHitReact && actionLockTicks <= 0 && hitReactCooldown <= 0) {
            hitReactCooldown = 10;

            if (dist <= BASH_RANGE && cdBash <= 0) {
                startAction(StudentBrAction.GUARD_BASH);
                return;
            }
            if (cdDodge <= 0) {
                startAction(StudentBrAction.DODGE_SHOT);
                return;
            }
        }


        // リロード進行（メイン）
        if (student.isReloading()) {
            student.tickReload(mainSpec);
        }

        // ===== 実行中アクション保持 =====
        if (actionHoldTicks > 0 && current != StudentBrAction.NONE) {
            student.requestBrAction(current, actionHoldTicks);
            runCurrent(current, dist, canSee, mainSpec, subSpec);

            actionHoldTicks--;
            actionAge++;
            return;
        }

// ★DODGEが終わるまで次行動に遷移しない（保険）
        if (current == StudentBrAction.DODGE_SHOT && actionLockTicks > 0) {
            return;
        }

// ★ここが肝：ロック中なら「何もしない」（次アクション開始禁止）
        if (actionLockTicks > 0) {
            mob.getNavigation().stop();
            student.requestBrAction(StudentBrAction.IDLE, 2); // 何も無ければIDLEでOK（好み）
            return;
        }

        // ===== 弾切れ：安全ならリロード開始＆SUB_RELOADへ =====
        if (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0) {
            if (dist < EVADE_DIST) {
                moveAwayFromTarget(SPEED_CHASE);
                student.requestBrAction(StudentBrAction.IDLE, 2);
                return;
            } else {
                if (!student.isReloading()) student.startReload(mainSpec);
                startAction(StudentBrAction.SUB_RELOAD_SHOT);
                return;
            }
        }

        // ===== 次アクション決定 =====
        StudentBrAction next = selectByDistance(dist, canSee, mainSpec);

        startAction(next);
    }

    private StudentBrAction selectByDistance(double dist, boolean canSee, WeaponSpec mainSpec) {
        if (mob.age % 20 == 0) {
            System.out.println("[BR] dist=" + dist
                    + " hurtTime=" + mob.hurtTime
                    + " cdTackle=" + cdTackle
                    + " cdBash=" + cdBash
                    + " cdDodge=" + cdDodge);
        }

        // ★接近用タックル（遠距離から詰める）
        if (dist > SHOTGUN_MAX && dist <= 12.0 && cdTackle <= 0) return StudentBrAction.GUARD_TACKLE;

        if (!canSee) return StudentBrAction.IDLE;



        if (dist <= TACKLE_RANGE && cdTackle <= 0) return StudentBrAction.GUARD_TACKLE;
        if (dist <= BASH_RANGE   && cdBash   <= 0) return StudentBrAction.GUARD_BASH;

        if (dist <= EVADE_DIST && cdDodge <= 0) return StudentBrAction.DODGE_SHOT;

        if (dist >= APPROACH_IF_OVER) return StudentBrAction.IDLE;

        // サイド（ショットガン距離帯でたまに）
        if (dist >= SHOTGUN_MIN && dist <= SHOTGUN_MAX) {
            if (cdSide <= 0 && cdSub <= 0 && mob.getRandom().nextFloat() < 0.22f) {
                return (mob.age % 2 == 0) ? StudentBrAction.LEFT_SIDE_SUB_SHOT : StudentBrAction.RIGHT_SIDE_SUB_SHOT;
            }
        }

        // リロード中はSUBで繋ぐ（3連射）
        if (student.isReloading()) return StudentBrAction.SUB_RELOAD_SHOT;

        // ショットガン距離：MAIN（単発を確実に撃つ）
        if (dist >= SHOTGUN_MIN && dist <= SHOTGUN_MAX) {
            if (cdMain <= 0) return StudentBrAction.MAIN_SHOT;
            if (cdSub <= 0)  return StudentBrAction.SUB_SHOT;
            return StudentBrAction.IDLE;
        }

        // 近い/遠い：SUBで牽制
        if (cdSub <= 0) return StudentBrAction.SUB_SHOT;
        return StudentBrAction.IDLE;
    }

    private void startAction(StudentBrAction a) {
        current = a;
        actionAge = 0;
        shotQueuedThisAction = false;
        meleeHitDone = false;

        // ★ここを戻す：行動の継続時間（runCurrentを回すために必須）
        actionHoldTicks = switch (a) {
            case GUARD_TACKLE, GUARD_BASH -> 10;
            case DODGE_SHOT               -> 17;
            case MAIN_SHOT                -> 6;   // 3でもOK
            case SUB_SHOT, SUB_RELOAD_SHOT -> 16;
            case LEFT_SIDE_SUB_SHOT, RIGHT_SIDE_SUB_SHOT -> 10;
            case IDLE -> 6;
            default -> 6;
        };

        // SUB burst準備
        if (a == StudentBrAction.SUB_SHOT || a == StudentBrAction.SUB_RELOAD_SHOT) {
            subBurstLeft = 3;
            subBurstCd = 0;
        } else {
            subBurstLeft = 0;
            subBurstCd = 0;
        }

        // ★割り込みロック（これはあなたのままでOK）
        actionLockTicks = switch (a) {
            case DODGE_SHOT -> 17;
            case GUARD_TACKLE, GUARD_BASH -> 10;
            case LEFT_SIDE_SUB_SHOT, RIGHT_SIDE_SUB_SHOT -> 8;
            case SUB_SHOT, SUB_RELOAD_SHOT -> 6;
            default -> 0;
        };

        // CD
        switch (a) {
            case GUARD_TACKLE -> cdTackle = 35;
            case GUARD_BASH   -> cdBash   = 25;
            case DODGE_SHOT   -> cdDodge  = 14;
            case LEFT_SIDE_SUB_SHOT, RIGHT_SIDE_SUB_SHOT -> cdSide = 24;
            default -> {}
        }

        student.requestBrAction(a, actionHoldTicks);
    }

    private void stopAction() {
        current = StudentBrAction.NONE;
        actionHoldTicks = 0;
        actionAge = 0;
        subBurstLeft = 0;
        subBurstCooldown = 0;
        shotQueuedThisAction = false;
    }

    private void runCurrent(StudentBrAction a, double dist, boolean canSee, WeaponSpec mainSpec, WeaponSpec subSpec) {
        if (target != null) student.requestLookTarget(target, 80, 2);

        if (!canSee) {
            moveTowardTarget(SPEED_AIM);
            return;
        }


        switch (a) {
            case IDLE -> {
                if (dist >= APPROACH_IF_OVER || dist > SHOTGUN_MAX) moveTowardTarget(SPEED_CHASE);
                else if (dist < EVADE_DIST) moveAwayFromTarget(SPEED_CHASE);
                else mob.getNavigation().stop();
            }

            case GUARD_TACKLE -> {
                mob.getNavigation().stop();

                // ★突進：最初の 4tick だけ前方へ（2〜3ブロック程度に収める）
                if (actionAge <= 4) {
                    Vec3d to = target.getPos().subtract(mob.getPos());
                    Vec3d flat = new Vec3d(to.x, 0, to.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();

                        // 速度を下げる：0.55〜0.75 くらい（4tickなら 2〜3ブロック感）
                        double dashSpeed = 0.65;
                        mob.setVelocity(dir.x * dashSpeed, mob.getVelocity().y, dir.z * dashSpeed);
                        mob.velocityDirty = true;

                        if (mob instanceof AbstractStudentEntity ase) {
                            ase.requestLookMoveDir(80, 2);
                        }
                    }
                }

                // ★ヒット判定：1回だけ
                if (!meleeHitDone && canSee && isTouchingTarget(1.05)) {
                    meleeHitDone = true;

                    // ダメージ
                    target.damage(mob.getDamageSources().mobAttack(mob), 6.0f);

                    // ノックバック（相手を押し出す）
                    Vec3d to = target.getPos().subtract(mob.getPos());
                    Vec3d flat = new Vec3d(to.x, 0, to.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();
                        target.takeKnockback(1.25, dir.x, dir.z);
                    }
                }
            }
            case GUARD_BASH -> {
                mob.getNavigation().stop();

                // バッシュは移動しない（見た目が安定）
                if (!meleeHitDone && canSee && isTouchingTarget(1.20)) {
                    meleeHitDone = true;

                    target.damage(mob.getDamageSources().mobAttack(mob), 4.0f);

                    Vec3d to = target.getPos().subtract(mob.getPos());
                    Vec3d flat = new Vec3d(to.x, 0, to.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();
                        target.takeKnockback(1.6, dir.x, dir.z); // ★強め
                    }
                }
            }

            case DODGE_SHOT -> {
                mob.getNavigation().stop();

                if (actionAge <= 6) { // ★最初だけ後退を固定
                    Vec3d away = mob.getPos().subtract(target.getPos());
                    Vec3d flat = new Vec3d(away.x, 0, away.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();
                        double dodgeSpeed = 0.95;
                        mob.setVelocity(dir.x * dodgeSpeed, mob.getVelocity().y, dir.z * dodgeSpeed);
                        mob.velocityDirty = true;

                        if (mob instanceof AbstractStudentEntity ase) {
                            ase.requestLookMoveDir(80, 2);
                        }
                    }
                }

                queueSingleShotAtTick(false, mainSpec, 4);
            }


            case MAIN_SHOT -> {
                mob.getNavigation().stop();
                queueSingleShotAtTick(false, mainSpec, 0); // ★即発射
            }

            case RIGHT_SIDE_SUB_SHOT -> {
                if (actionAge == 0) startSideStepDash(false, 1.05, 2); // ★速く・短く
                tickSideDash();
                queueSingleShotAtTick(true, subSpec, 1);
            }

            case LEFT_SIDE_SUB_SHOT -> {
                if (actionAge == 0) startSideStepDash(true, 1.05, 2);
                tickSideDash();
                queueSingleShotAtTick(true, subSpec, 1);
            }

            case SUB_SHOT, SUB_RELOAD_SHOT -> {
                mob.getNavigation().stop();
                System.out.println("[SUB] queued subBurstLeft=" + subBurstLeft + " cd=" + subBurstCd);
                tickSubBurst(subSpec);   // ★これがないと一生撃たない
            }

            default -> mob.getNavigation().stop();
        }
    }

    private void queueSingleShotAtTick(boolean isSub, WeaponSpec spec, int triggerTick) {
        if (target == null || !target.isAlive()) return;
        if (shotQueuedThisAction) return;
        if (actionAge < triggerTick) return;

        if (!isSub) {
            if (student.hasQueuedFire(IStudentEntity.FireChannel.MAIN)) return;
            student.queueFire(target, IStudentEntity.FireChannel.MAIN);
            cdMain = Math.max(cdMain, spec.cooldownTicks);
            shotQueuedThisAction = true;
            return;
        }

        // ===== SUB =====
        IStudentEntity.FireChannel ch;

        // サイドは固定
        if (current == StudentBrAction.LEFT_SIDE_SUB_SHOT) ch = IStudentEntity.FireChannel.SUB_L;
        else if (current == StudentBrAction.RIGHT_SIDE_SUB_SHOT) ch = IStudentEntity.FireChannel.SUB_R;
        else {
            // 通常SUBは交互
            subFlip = !subFlip;
            ch = subFlip ? IStudentEntity.FireChannel.SUB_L : IStudentEntity.FireChannel.SUB_R;
        }

        if (student.hasQueuedFire(ch)) return;
        student.queueFire(target, ch);
        cdSub = Math.max(cdSub, spec.cooldownTicks);

        shotQueuedThisAction = true;
    }

    private void tickSubBurst(WeaponSpec subSpec) {
        if (target == null || !target.isAlive()) return;
        if (subBurstLeft <= 0) return;

        if (subBurstCd > 0) { subBurstCd--; return; }

        // 交互
        subFlip = !subFlip;
        IStudentEntity.FireChannel ch = subFlip ? IStudentEntity.FireChannel.SUB_L : IStudentEntity.FireChannel.SUB_R;

        if (student.hasQueuedFire(ch)) return;

        student.queueFire(target, ch);
        subBurstLeft--;

        subBurstCd = Math.max(1, subSpec.cooldownTicks);
    }

    // ===== Movement =====
    private void moveTowardTarget(double speed) {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;
        Vec3d pos = target.getPos();
        mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
    }

    private void moveAwayFromTarget(double speed) {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;

        Vec3d desired = mob.getPos().add(away.normalize().multiply(6.0));
        Vec3d fuzzy = FuzzyTargeting.findFrom(mob, 12, 7, desired);
        if (fuzzy == null) fuzzy = desired;

        mob.getNavigation().startMovingTo(fuzzy.x, fuzzy.y, fuzzy.z, speed);
    }

    private void startSideStepDash(boolean left, double horizSpeed, int dashTicks) {
        if (target == null) return;

        Vec3d to = target.getPos().subtract(mob.getPos());
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        if (flat.lengthSquared() < 1.0e-6) return;

        Vec3d dir = flat.normalize();
        Vec3d side = left
                ? new Vec3d(-dir.z, 0, dir.x)
                : new Vec3d(dir.z, 0, -dir.x);

        mob.getNavigation().stop();

        sideDashVel = side.normalize().multiply(horizSpeed);
        sideDashTicksLeft = Math.max(1, dashTicks);

        if (mob instanceof AbstractStudentEntity ase) {
            ase.requestLookMoveDir(80, dashTicks);
        }
    }

    // 毎tick呼ぶ（runCurrent の side case の先頭で呼ぶ）
    private void tickSideDash() {
        if (sideDashTicksLeft <= 0) return;

        // ★毎tick速度を維持 → “シュッ”
        mob.setVelocity(sideDashVel.x, mob.getVelocity().y, sideDashVel.z);
        mob.velocityDirty = true;

        sideDashTicksLeft--;
    }

    // ===== cooldowns =====
    private void clearCds() {
        cdTackle = cdBash = cdDodge = cdMain = cdSub = cdSide = 0;
    }

    private void tickCds() {
        if (cdTackle > 0) cdTackle--;
        if (cdBash   > 0) cdBash--;
        if (cdDodge  > 0) cdDodge--;
        if (cdMain   > 0) cdMain--;
        if (cdSub    > 0) cdSub--;
        if (cdSide   > 0) cdSide--;
    }

    // ===== target =====
    private LivingEntity findTarget() {
        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        Box box = mob.getBoundingBox().expand(main.range + DETECT_EXTRA);
        return mob.getWorld()
                .getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof HostileEntity)
                .stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
    private boolean isTouchingTarget(double expand) {
        if (target == null) return false;
        return mob.getBoundingBox().expand(expand).intersects(target.getBoundingBox());
    }

}