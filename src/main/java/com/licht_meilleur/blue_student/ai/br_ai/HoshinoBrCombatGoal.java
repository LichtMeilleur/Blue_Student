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

    // ===== distance tactics (tune here) =====
    private static final double DETECT_EXTRA = 10.0;

    private static final double TACKLE_RANGE = 2.8;
    private static final double BASH_RANGE   = 3.4;

    // ショットガンで撃ちたい距離帯（ここが“理想”）
    private static final double SHOTGUN_MIN = 4.0;
    private static final double SHOTGUN_MAX = 8.5;

    // 近すぎたら回避（距離を開ける）
    private static final double EVADE_DIST = 2.4;

    // 遠い時は詰める（撃つよりまず距離を作る）
    private static final double APPROACH_IF_OVER = 10.0;

    // 移動速度
    private static final double SPEED_CHASE = 1.35;
    private static final double SPEED_AIM   = 1.15;
    private static final int REPATH_INTERVAL = 12;
    private int repathCooldown = 0;

    // ===== action hold =====
    private StudentBrAction current = StudentBrAction.NONE;
    private int actionHoldTicks = 0;     // アニメ保持用（BR_ACTION_HOLDに投げる）
    private int actionAge = 0;

    // ===== per-action cooldowns =====
    private int cdTackle = 0;
    private int cdBash   = 0;
    private int cdDodge  = 0;
    private int cdMain   = 0;
    private int cdSub    = 0;

    // burst（「同じ行動の中で複数回キューを積む」）
    private int burstLeft = 0;
    private int burstTick = 0;

    // ===== strategy memory =====
    private int noTargetTicks = 0;

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

        // BRの主武装射程を基準に追跡継続
        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        double keep = main.range + DETECT_EXTRA;
        return mob.squaredDistanceTo(target) <= keep * keep;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        clearCds();
        stopAction();
        noTargetTicks = 0;
        mob.getNavigation().stop();
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
        if (!(mob.getWorld() instanceof ServerWorld sw)) return;

        // cd tick
        tickCds();
        if (repathCooldown > 0) repathCooldown--;

        // target refresh
        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            noTargetTicks++;
            return;
        }
        noTargetTicks = 0;

        final double dist = mob.distanceTo(target);
        final boolean canSee = mob.getVisibilityCache().canSee(target);

        // Spec（必要ならこの値でrange/cd/弾を使い分け）
        final WeaponSpec mainSpec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        final WeaponSpec subSpec  = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, true);

        // ★リロード進行（BRでもここで回す）
        if (student.isReloading()) {
            student.tickReload(mainSpec);
        }

        // ===== 1) 実行中アクションの保持 =====
        if (actionHoldTicks > 0 && current != StudentBrAction.NONE) {
            // 毎tick保持（これが“ワンテンポ”減らす）
            student.requestBrAction(current, actionHoldTicks);

            runCurrent(current, dist, canSee, mainSpec, subSpec);

            actionHoldTicks--;
            actionAge++;
            return;
        }

        // ===== 2) 次アクション決定（距離ベース） =====
        StudentBrAction next = selectByDistance(dist, canSee, mainSpec, subSpec);

        // ===== 3) アクション開始 =====
        startAction(next);
    }

    // =========================================================
    // Distance-based selection
    // =========================================================
    private StudentBrAction selectByDistance(double dist, boolean canSee, WeaponSpec mainSpec, WeaponSpec subSpec) {

        // 見えないなら詰める（撃たない）
        if (!canSee) return StudentBrAction.IDLE;

        // 近すぎ：回避しつつ撃つ（DODGE_SHOTがあるなら最優先）
        if (dist <= EVADE_DIST && cdDodge <= 0) {
            return StudentBrAction.DODGE_SHOT;
        }

        // 近距離：タックル/バッシュ（CD見ながら）
        if (dist <= TACKLE_RANGE && cdTackle <= 0) return StudentBrAction.GUARD_TACKLE;
        if (dist <= BASH_RANGE   && cdBash   <= 0) return StudentBrAction.GUARD_BASH;

        // 遠い：まず詰める（撃つより距離を作る）
        if (dist >= APPROACH_IF_OVER) return StudentBrAction.IDLE;

        // メイン弾切れ or リロード中：SUBで繋ぐ（ショットガン中断しない）
        if (student.isReloading() || (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0)) {
            // 連射感を出すならSUB_RELOAD_SHOTを優先
            return StudentBrAction.SUB_RELOAD_SHOT;
        }

        // ショットガン距離帯：MAIN_SHOT
        if (dist >= SHOTGUN_MIN && dist <= SHOTGUN_MAX) {
            if (cdMain <= 0) return StudentBrAction.MAIN_SHOT;
            // MAINがCD中はSUBで“つなぎ撃ち”してテンポを維持
            if (cdSub <= 0) return StudentBrAction.SUB_SHOT;
            return StudentBrAction.IDLE;
        }

        // ショットガンより近い（でも近すぎではない）：少し下がりながら撃つ → SUB
        if (dist < SHOTGUN_MIN) {
            if (cdSub <= 0) return StudentBrAction.SUB_SHOT;
            return StudentBrAction.IDLE;
        }

        // ショットガンより遠い：詰めつつSUBで牽制（好み）
        if (dist > SHOTGUN_MAX) {
            if (cdSub <= 0) return StudentBrAction.SUB_SHOT;
            return StudentBrAction.IDLE;
        }

        return StudentBrAction.IDLE;
    }

    // =========================================================
    // Action begin/stop
    // =========================================================
    private void startAction(StudentBrAction a) {
        current = a;
        actionAge = 0;

        // hold ticks（アニメ保持と“間”消し）
        actionHoldTicks = switch (a) {
            case GUARD_TACKLE, GUARD_BASH -> 10;
            case DODGE_SHOT               -> 8;
            case MAIN_SHOT                -> 6;
            case SUB_SHOT, SUB_RELOAD_SHOT,
                 RIGHT_SIDE_SUB_SHOT, LEFT_SIDE_SUB_SHOT -> 5;
            case IDLE -> 6;
            default -> 6;
        };

        // burst（同じアクション中に複数回キュー積み）
        burstLeft = switch (a) {
            case MAIN_SHOT -> 2;
            case SUB_SHOT  -> 3;
            case SUB_RELOAD_SHOT -> 2;
            case DODGE_SHOT -> 2;
            default -> 0;
        };
        burstTick = 0;

        // CDセット（“行動自体のCD”）
        switch (a) {
            case GUARD_TACKLE -> cdTackle = 35;
            case GUARD_BASH   -> cdBash   = 25;
            case DODGE_SHOT   -> cdDodge  = 14;
            case MAIN_SHOT    -> cdMain   = 0;  // ここは mainSpec.cooldownTicks で回す方が自然なら0でもOK
            case SUB_SHOT, SUB_RELOAD_SHOT -> cdSub = 0;
        }

        // BRアニメ開始通知
        student.requestBrAction(a, actionHoldTicks);
    }

    private void stopAction() {
        current = StudentBrAction.NONE;
        actionHoldTicks = 0;
        actionAge = 0;
        burstLeft = 0;
        burstTick = 0;
    }

    // =========================================================
    // Action execution
    // =========================================================
    private void runCurrent(StudentBrAction a, double dist, boolean canSee, WeaponSpec mainSpec, WeaponSpec subSpec) {

        // 基本：視線は維持
        if (target != null) student.requestLookTarget(target, 80, 2);

        // 見えないならとにかく詰める
        if (!canSee) {
            moveTowardTarget(SPEED_AIM);
            return;
        }

        switch (a) {
            case IDLE -> {
                // 遠いなら詰める、近すぎなら離れる、適正なら止まる
                if (dist >= APPROACH_IF_OVER || dist > SHOTGUN_MAX) {
                    moveTowardTarget(SPEED_CHASE);
                } else if (dist < EVADE_DIST) {
                    moveAwayFromTarget(SPEED_CHASE);
                } else {
                    mob.getNavigation().stop();
                }
            }

            case GUARD_TACKLE, GUARD_BASH -> {
                // 近距離コンボ：基本は止めて当てる（あなたのrunTackle/runBash相当はここに移植してもOK）
                mob.getNavigation().stop();
                // 必要なら「ここで突進/ノックバック処理」を入れる
                // 今はアニメ保持だけして、射撃はしない
            }

            case DODGE_SHOT -> {
                // 近すぎ対策：少し下がりながらSUB撃ち
                if (dist < SHOTGUN_MIN) moveAwayFromTarget(SPEED_AIM);
                else mob.getNavigation().stop();
                burstFire(true, subSpec);
            }

            case MAIN_SHOT -> {
                // ショットガン距離を保つ：遠いなら寄る、近いなら離れる
                if (dist > SHOTGUN_MAX) moveTowardTarget(SPEED_AIM);
                else if (dist < SHOTGUN_MIN) moveAwayFromTarget(SPEED_AIM);
                else mob.getNavigation().stop();

                // メイン射撃キュー（AimGoalが撃つ）
                burstFire(false, mainSpec);
            }

            case SUB_RELOAD_SHOT, SUB_SHOT, RIGHT_SIDE_SUB_SHOT, LEFT_SIDE_SUB_SHOT -> {
                // サブ：距離維持（近いなら離れる）
                if (dist < SHOTGUN_MIN) moveAwayFromTarget(SPEED_AIM);
                else if (dist > SHOTGUN_MAX + 1.5) moveTowardTarget(SPEED_AIM);
                else mob.getNavigation().stop();

                burstFire(true, subSpec);
            }

            default -> mob.getNavigation().stop();
        }
    }

    private void burstFire(boolean isSub, WeaponSpec spec) {
        if (target == null) return;

        // burstは「数tickに1回キュー積み」
        burstTick++;

        // 2tickに1回くらい（好み）
        if (burstLeft <= 0) return;
        if (burstTick % 2 != 0) return;

        // per-weapon cooldownを使いたいならここで抑制
        if (!isSub && cdMain > 0) return;
        if ( isSub && cdSub  > 0) return;

        // キューを積む（AimGoalが実弾を発射）
        if (isSub) student.queueFireSub(target);
        else       student.queueFire(target);

        burstLeft--;

        // 連射間隔：WeaponSpecのcooldownTicksで回す（あなたの狙いどおり）
        if (!isSub) cdMain = Math.max(cdMain, spec.cooldownTicks);
        else        cdSub  = Math.max(cdSub,  spec.cooldownTicks);
    }

    // =========================================================
    // Movement helpers
    // =========================================================
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

    // =========================================================
    // Cooldowns
    // =========================================================
    private void clearCds() {
        cdTackle = cdBash = cdDodge = cdMain = cdSub = 0;
    }

    private void tickCds() {
        if (cdTackle > 0) cdTackle--;
        if (cdBash   > 0) cdBash--;
        if (cdDodge  > 0) cdDodge--;
        if (cdMain   > 0) cdMain--;
        if (cdSub    > 0) cdSub--;
    }

    // =========================================================
    // Targeting
    // =========================================================
    private LivingEntity findTarget() {
        WeaponSpec main = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        Box box = mob.getBoundingBox().expand(main.range + DETECT_EXTRA);

        return mob.getWorld()
                .getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof HostileEntity)
                .stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}