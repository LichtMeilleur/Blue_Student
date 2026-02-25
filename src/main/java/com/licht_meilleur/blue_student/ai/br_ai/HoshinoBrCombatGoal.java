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
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

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

    // ===== side dash =====
    private int sideDashTicksLeft = 0;
    private Vec3d sideDashVel = Vec3d.ZERO;

    // ===== hit react =====
    private int lastHurtTime = 0;

    // ===== melee =====
    private boolean meleeHitDone = false;

    // ★アニメを流し切るまで割り込み禁止にするロック
    private int actionLockTicks = 0;
    private int hitReactCooldown = 0;

    // ===== wall-hug対策 =====
    private int noSeeTicks = 0;
    private static final int DROP_TARGET_NOSEE_TICKS = 35; // 見えない状態が続いたらターゲット捨てる
    private static final int REACQUIRE_INTERVAL = 10;      // 何tickごとに再探索するか

    // ===== SUB 3点バースト（sub_shot3 / sub_reload_shot3 の発射タイミング）=====
    // 2,6,10tick で3発（必要ならここを調整）
    private static final int[] SUB_BURST_TICKS = new int[]{0, 4, 8};

    private double prevDist = 9999.0;

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

        target = findTargetPreferVisibleAndPath();
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

        sideDashTicksLeft = 0;
        sideDashVel = Vec3d.ZERO;
        meleeHitDone = false;
        actionLockTicks = 0;
        hitReactCooldown = 0;
        lastHurtTime = 0;

        noSeeTicks = 0;
        prevDist = 9999.0;
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
        stopAction();
        clearCds();
        noSeeTicks = 0;
        prevDist = 9999.0;
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return;

        tickCds();
        if (hitReactCooldown > 0) hitReactCooldown--;
        if (repathCooldown > 0) repathCooldown--;
        if (cdSide > 0) cdSide--;
        if (actionLockTicks > 0) actionLockTicks--;

        // target refresh
        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            prevDist = 9999.0; // ★追加
            return;
        }

        final double dist = mob.distanceTo(target);
        final boolean canSee = mob.getVisibilityCache().canSee(target);

// ★境界跨ぎでDODGE発動（3→2に入った瞬間）
        boolean crossedDodgeBand = (prevDist > 3.0 && dist <= 2.0);

// 次tickのために保存（targetが変わる/死ぬ前に一旦入れてOK）
        prevDist = dist;

        if (crossedDodgeBand
                && canSee                       // 任意（壁越しで発動しない）
                && cdDodge <= 0
                && actionLockTicks <= 0         // あなたのロック運用に合わせて
                && hitReactCooldown <= 0        // 任意
                && current != StudentBrAction.DODGE_SHOT) {

            startAction(StudentBrAction.DODGE_SHOT);
            return;
        }

        final WeaponSpec mainSpec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        final WeaponSpec subSpec  = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.SUB_L);

        // ===== wall-hug対策：見えない時間カウント =====
        if (!canSee) noSeeTicks++;
        else noSeeTicks = 0;

        // 見えない状態が続くなら「壁の向こうを追ってる」可能性が高いので捨てる
        if (noSeeTicks >= DROP_TARGET_NOSEE_TICKS) {
            target = findTargetPreferVisibleAndPath();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            noSeeTicks = 0;
            return;
        }

        // 一定間隔で「より良い可視ターゲット」がいないか再探索（壁越し固定を弱める）
        if (mob.age % REACQUIRE_INTERVAL == 0) {
            LivingEntity better = findTargetPreferVisibleAndPath();
            if (better != null && better != target) {
                target = better;
                mob.setTarget(target);
                mob.getNavigation().stop();
                stopAction();
                // ここでreturnしない（同tickで次行動に行ってOK）
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

        // ★重要：ロック中は「今のcurrentを握り続ける」→ アニメ空白tickを作らない
        if (actionLockTicks > 0) {
            if (current != StudentBrAction.NONE) {
                student.requestBrAction(current, 2);
            }
            mob.getNavigation().stop();
            return;
        }

        // ===== 弾切れ：安全ならリロード開始＆SUB_RELOADへ =====
        if (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0) {
            if (dist < EVADE_DIST) {
                moveAwayFromTarget(SPEED_CHASE);
                stopAction(); // ★移動runを優先（BR actionを握らない）
                return;
            } else {
                if (!student.isReloading()) student.startReload(mainSpec);
                startAction(StudentBrAction.SUB_RELOAD_SHOT);
                return;
            }
        }

        // ===== 見えない：壁に張り付かないよう “横にずれる” を優先 =====
        if (!canSee) {
            // 近いのに見えない＝壁越しの可能性が高い → 側方へずれて視界を探す
            if (dist < 6.0) {
                moveSideToFindLoS();
            } else {
                moveTowardTarget(SPEED_AIM);
            }
            stopAction();
            return;
        }

        // ===== 遠い：接近（BR actionは握らず run に任せる）=====
        if (dist >= APPROACH_IF_OVER) {
            moveTowardTarget(SPEED_CHASE);
            stopAction();
            return;
        }

        // ===== 次アクション決定 =====
        StudentBrAction next = selectByDistance(dist, mainSpec);

        if (next == StudentBrAction.NONE) {
            stopAction();
            return;
        }

        startAction(next);
    }

    private StudentBrAction selectByDistance(double dist, WeaponSpec mainSpec) {

        // ★接近用タックル（遠距離から詰める）
        if (dist > SHOTGUN_MAX && dist <= 12.0 && cdTackle <= 0) return StudentBrAction.GUARD_TACKLE;

        if (dist <= TACKLE_RANGE && cdTackle <= 0) return StudentBrAction.GUARD_TACKLE;
        if (dist <= BASH_RANGE   && cdBash   <= 0) return StudentBrAction.GUARD_BASH;

        if (dist <= EVADE_DIST && cdDodge <= 0) return StudentBrAction.DODGE_SHOT;

        // サイド（ショットガン距離帯でたまに）
        if (dist >= SHOTGUN_MIN && dist <= SHOTGUN_MAX) {
            if (cdSide <= 0 && cdSub <= 0 && mob.getRandom().nextFloat() < 0.22f) {
                return (mob.age % 2 == 0) ? StudentBrAction.LEFT_SIDE_SUB_SHOT : StudentBrAction.RIGHT_SIDE_SUB_SHOT;
            }
        }

        // リロード中はSUBで繋ぐ（3点バースト）
        if (student.isReloading()) return StudentBrAction.SUB_RELOAD_SHOT;

        // ショットガン距離：MAIN / SUB
        if (dist >= SHOTGUN_MIN && dist <= SHOTGUN_MAX) {
            if (cdMain <= 0) return StudentBrAction.MAIN_SHOT;
            if (cdSub <= 0)  return StudentBrAction.SUB_SHOT;
            return StudentBrAction.NONE;
        }

        // 近い/遠い：SUBで牽制
        if (cdSub <= 0) return StudentBrAction.SUB_SHOT;

        return StudentBrAction.NONE;
    }

    private void startAction(StudentBrAction a) {
        current = a;
        actionAge = 0;
        meleeHitDone = false;

        // ★長さはアニメに合わせる（sub_shot3が10tick目まで撃つので16でOK）
        actionHoldTicks = switch (a) {
            case GUARD_TACKLE, GUARD_BASH -> 10;
            case DODGE_SHOT               -> 17;
            case MAIN_SHOT                -> 6;
            case SUB_SHOT, SUB_RELOAD_SHOT -> 16;
            case LEFT_SIDE_SUB_SHOT, RIGHT_SIDE_SUB_SHOT -> 10;
            default -> 6;
        };

        // ★割り込みロック（DODGEなどを守る）
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
        meleeHitDone = false;
    }

    private void runCurrent(StudentBrAction a, double dist, boolean canSee, WeaponSpec mainSpec, WeaponSpec subSpec) {
        if (target != null) student.requestLookTarget(target, 80, 2);

        // 見えない時は「攻撃アクション中でも」移動だけ（無理に壁に撃たない）
        if (!canSee) {
            if (dist < 6.0) moveSideToFindLoS();
            else moveTowardTarget(SPEED_AIM);
            return;
        }

        switch (a) {
            case GUARD_TACKLE -> {
                mob.getNavigation().stop();

                // 突進：最初の 4tick だけ前方へ
                if (actionAge <= 4) {
                    Vec3d to = target.getPos().subtract(mob.getPos());
                    Vec3d flat = new Vec3d(to.x, 0, to.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();
                        double dashSpeed = 0.65;
                        mob.setVelocity(dir.x * dashSpeed, mob.getVelocity().y, dir.z * dashSpeed);
                        mob.velocityDirty = true;

                        if (mob instanceof AbstractStudentEntity ase) {
                            ase.requestLookMoveDir(80, 2);
                        }
                    }
                }

                // 命中判定：距離＋tick窓（通り抜け対策）
                if (!meleeHitDone && actionAge >= 2 && actionAge <= 8 && isInMeleeRange(2.6)) {
                    meleeHitDone = true;

                    target.damage(mob.getDamageSources().mobAttack(mob), 6.0f);

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

                if (!meleeHitDone && actionAge >= 1 && actionAge <= 6 && isInMeleeRange(2.8)) {
                    meleeHitDone = true;

                    target.damage(mob.getDamageSources().mobAttack(mob), 4.0f);

                    Vec3d to = target.getPos().subtract(mob.getPos());
                    Vec3d flat = new Vec3d(to.x, 0, to.z);
                    if (flat.lengthSquared() > 1.0e-6) {
                        Vec3d dir = flat.normalize();
                        target.takeKnockback(1.6, dir.x, dir.z);
                    }
                }
            }

            case DODGE_SHOT -> {
                mob.getNavigation().stop();

                if (actionAge <= 6) {
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

                queueSingleShotAtTick(false, mainSpec, 1);
            }

            case MAIN_SHOT -> {
                mob.getNavigation().stop();
                queueSingleShotAtTick(false, mainSpec, 0);
            }

            case RIGHT_SIDE_SUB_SHOT -> {
                if (actionAge == 0) startSideStepDash(false, 1.05, 2);
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

                // ★2,6,10tick で3発（sub_shot3 / sub_reload_shot3）
                for (int t : SUB_BURST_TICKS) {
                    queueBurstShotAtExactTick(true, subSpec, t);
                }
            }

            default -> {
                mob.getNavigation().stop();
            }
        }
    }

    private void queueSingleShotAtTick(boolean isSub, WeaponSpec spec, int triggerTick) {
        if (target == null || !target.isAlive()) return;
        if (actionAge < triggerTick) return;

        if (!isSub) {
            if (student.hasQueuedFire(IStudentEntity.FireChannel.MAIN)) return;
            student.queueFire(target, IStudentEntity.FireChannel.MAIN);
            cdMain = Math.max(cdMain, spec.cooldownTicks);
            return;
        }

        // ★ホシノは SUB_L 固定（sub_muzzle）
        IStudentEntity.FireChannel ch = IStudentEntity.FireChannel.SUB_L;

        if (student.hasQueuedFire(ch)) return;
        student.queueFire(target, ch);
        cdSub = Math.max(cdSub, spec.cooldownTicks);
    }

    // ★固定tickでだけ1発キューする（同一tickで多重に積まないため "==" を使う）
    private void queueBurstShotAtExactTick(boolean isSub, WeaponSpec spec, int exactTick) {
        if (target == null || !target.isAlive()) return;
        if (actionAge != exactTick) return;

        IStudentEntity.FireChannel ch = isSub ? IStudentEntity.FireChannel.SUB_L : IStudentEntity.FireChannel.MAIN;
        if (student.hasQueuedFire(ch)) return;

        student.queueFire(target, ch);

        if (isSub) cdSub = Math.max(cdSub, spec.cooldownTicks);
        else       cdMain = Math.max(cdMain, spec.cooldownTicks);
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

    // ★見えない時：横にずれてLoSを探す（壁張り付き軽減）
    private void moveSideToFindLoS() {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        if (target == null) return;

        Vec3d to = target.getPos().subtract(mob.getPos());
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        if (flat.lengthSquared() < 1.0e-6) return;

        Vec3d dir = flat.normalize();
        boolean left = mob.getRandom().nextBoolean();
        Vec3d side = left ? new Vec3d(-dir.z, 0, dir.x) : new Vec3d(dir.z, 0, -dir.x);

        // 2.5〜4.0ブロック横にずれる
        double len = 2.5 + mob.getRandom().nextDouble() * 1.5;
        Vec3d desired = mob.getPos().add(side.normalize().multiply(len));

        Vec3d fuzzy = FuzzyTargeting.findFrom(mob, 10, 6, desired);
        if (fuzzy == null) fuzzy = desired;

        mob.getNavigation().startMovingTo(fuzzy.x, fuzzy.y, fuzzy.z, SPEED_AIM);
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

    private void tickSideDash() {
        if (sideDashTicksLeft <= 0) return;

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
    // ★「見える敵」優先 + 「到達可能パス」優先
    private LivingEntity findTargetPreferVisibleAndPath() {
        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        Box box = mob.getBoundingBox().expand(main.range + DETECT_EXTRA);

        List<LivingEntity> list = mob.getWorld()
                .getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof HostileEntity);

        if (list.isEmpty()) return null;

        // 1) 見えてる + パスある
        LivingEntity best = list.stream()
                .filter(e -> mob.getVisibilityCache().canSee(e))
                .filter(this::hasPathTo)
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
        if (best != null) return best;

        // 2) 見えてる（パスは問わない）
        best = list.stream()
                .filter(e -> mob.getVisibilityCache().canSee(e))
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
        if (best != null) return best;

        // 3) パスある（見えなくても到達できるなら追う価値あり）
        best = list.stream()
                .filter(this::hasPathTo)
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
        if (best != null) return best;

        // 4) 最後の手段：単純最寄り
        return list.stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }

    private boolean hasPathTo(LivingEntity e) {
        if (e == null) return false;
        // findPathTo は重いのでターゲット探索時だけ使う（tickごとにやらない）
        Path p = mob.getNavigation().findPathTo(e, 0);
        if (p == null) return false;
        // reachesTarget が false でも “壁越しで遠回り” はあるので、好みで true 限定でもOK
        return true;
    }

    // ★近接は距離判定が安定
    private boolean isInMeleeRange(double range) {
        if (target == null) return false;
        return mob.squaredDistanceTo(target) <= range * range;
    }
    private LivingEntity findTarget() {
        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, IStudentEntity.FireChannel.MAIN);
        Box box = mob.getBoundingBox().expand(main.range + DETECT_EXTRA);

        return mob.getWorld()
                .getEntitiesByClass(LivingEntity.class, box,
                        e -> e.isAlive() && e instanceof HostileEntity)
                .stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}