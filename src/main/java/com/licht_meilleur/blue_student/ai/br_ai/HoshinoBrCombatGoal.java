package com.licht_meilleur.blue_student.ai.br_ai;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

public class HoshinoBrCombatGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;
    private LivingEntity target;

    // ===== 距離レンジ（調整用）=====
    private static final double DODGE_SHOT_DIST = 3.0;   // 近すぎると後退撃ち
    private static final double MAIN_SHOT_MIN   = 2.5;   // メイン通常射撃距離
    private static final double MAIN_SHOT_MAX   = 6.0;

    private static final double SIDE_SHOT_MIN = 3.0;
    private static final double SIDE_SHOT_MAX = 9.0;

    // ===== 移動速度 =====
    private static final double STRAFE_SPEED = 1.25;

    // ===== リパス頻度（tryMoveAwayで使う）=====
    private static final int REPATH_INTERVAL = 8;
    private int repathCooldown = 0;

    // ===== コマンド式アクション =====
    private StudentBrAction current = StudentBrAction.NONE;
    private int actionTicksLeft = 0;
    private boolean actionStarted = false;     // 開始tickだけ true
    private int actionAge = 0;                // 現アクション開始からの経過tick

    // actionごとのCD（ticks）
    private final int[] cds = new int[StudentBrAction.values().length];

    // SUB_RELOAD_SHOT 給弾用
    private int reloadStepCounter = 0;

    public HoshinoBrCombatGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private boolean isBr() {
        if (student instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity ase) {
            return ase.getForm() == StudentForm.BR;
        }
        return false;
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

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        double keep = spec.range + 10.0;
        return mob.squaredDistanceTo(target) <= keep * keep;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        clearAllCds();
        stopAction();
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
        stopAction();
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;

        tickCooldowns();
        if (repathCooldown > 0) repathCooldown--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            return;
        }

        WeaponSpec mainSpec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        double dist = mob.distanceTo(target);
        boolean canSee = mob.getVisibilityCache().canSee(target);

        // ① 実行中アクションがあるなら “それだけ”
        if (actionTicksLeft > 0 && current != StudentBrAction.NONE) {
            // ★毎tick「現在アクション」を投げる（保持安定）
            student.requestBrAction(current, actionTicksLeft);

            runCurrentAction(mainSpec, dist, canSee);

            actionTicksLeft--;
            actionAge++;
            actionStarted = false;
            return;
        }

        // ② 次アクション選択
        StudentBrAction next = selectNextAction(mainSpec, dist, canSee);

        // ③ duration/cd をここで一括定義（好みで調整）
        switch (next) {
            case DODGE_SHOT -> startAction(next, 8, 20);
            case RIGHT_SIDE_SUB_SHOT, LEFT_SIDE_SUB_SHOT -> startAction(next, 6, 10);
            case SUB_RELOAD_SHOT -> startAction(next, 8, 5);
            case MAIN_SHOT -> startAction(next, 8, 10);
            case SUB_SHOT -> startAction(next, 6, 0);
            default -> startAction(StudentBrAction.SUB_SHOT, 6, 0);
        }
    }

    // ===== 次アクション選択 =====
    private StudentBrAction selectNextAction(WeaponSpec mainSpec, double dist, boolean canSee) {

        // リロード中 → SUB_RELOAD_SHOT
        if (student.isReloading()) return StudentBrAction.SUB_RELOAD_SHOT;

        // メイン弾切れ → まずサブで粘る（距離近いならDODGE）
        if (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0) {
            if (dist <= DODGE_SHOT_DIST && !onCd(StudentBrAction.DODGE_SHOT)) return StudentBrAction.DODGE_SHOT;
            return StudentBrAction.SUB_RELOAD_SHOT;
        }

        // 近距離：DODGE（CD見てダメならMAIN or SUBへ）
        if (dist <= DODGE_SHOT_DIST && !onCd(StudentBrAction.DODGE_SHOT)) {
            return StudentBrAction.DODGE_SHOT;
        }

        // メイン通常射撃（距離が合って見えてる）
        if (canSee && dist >= MAIN_SHOT_MIN && dist <= MAIN_SHOT_MAX && !onCd(StudentBrAction.MAIN_SHOT)) {
            return StudentBrAction.MAIN_SHOT;
        }

        // SIDE（空いてる側優先）
        if (canSee && dist >= SIDE_SHOT_MIN && dist <= SIDE_SHOT_MAX) {
            StudentBrAction side = chooseSideAction();
            if (side != StudentBrAction.NONE && !onCd(side)) return side;
        }

        return StudentBrAction.SUB_SHOT;
    }

    private StudentBrAction chooseSideAction() {
        // 右/左の目標座標を作り、到達できる方を優先
        Vec3d rp = computeStrafePos(target, true, 5.0);
        Vec3d lp = computeStrafePos(target, false, 5.0);

        boolean rOk = canReach(rp);
        boolean lOk = canReach(lp);

        if (rOk && !lOk) return StudentBrAction.RIGHT_SIDE_SUB_SHOT;
        if (!rOk && lOk) return StudentBrAction.LEFT_SIDE_SUB_SHOT;

        if (rOk) {
            // 両方OKなら “いまは右優先” とかにしてもいい（ここは好み）
            return StudentBrAction.RIGHT_SIDE_SUB_SHOT;
        }
        return StudentBrAction.NONE;
    }

    // ===== アクション開始/停止 =====
    private void startAction(StudentBrAction a, int duration, int cooldown) {
        current = a;
        actionTicksLeft = Math.max(1, duration);
        actionStarted = true;
        actionAge = 0;

        // SUB_RELOAD_SHOT の給弾カウンタ
        if (a == StudentBrAction.SUB_RELOAD_SHOT) reloadStepCounter = 0;

        student.requestBrAction(a, duration);
        if (cooldown > 0) setCd(a, cooldown);
    }

    private void stopAction() {
        current = StudentBrAction.NONE;
        actionTicksLeft = 0;
        actionStarted = false;
        actionAge = 0;
        reloadStepCounter = 0;
    }

    // ===== アクション実行 =====
    private void runCurrentAction(WeaponSpec mainSpec, double dist, boolean canSee) {
        switch (current) {
            case DODGE_SHOT -> runDodgeShot(mainSpec, canSee);
            case RIGHT_SIDE_SUB_SHOT -> runSideSubShot(true, canSee);
            case LEFT_SIDE_SUB_SHOT -> runSideSubShot(false, canSee);
            case SUB_RELOAD_SHOT -> runSubReloadShot(mainSpec, dist, canSee);
            case SUB_SHOT -> runSubShot(canSee);
            case MAIN_SHOT -> runMainShot(canSee);
            default -> {
                mob.getNavigation().stop();
                student.requestLookTarget(target, 80, 2);
            }
        }
    }

    private void runDodgeShot(WeaponSpec mainSpec, boolean canSee) {
        // 視線：敵（敵を見ながら後退撃ち）
        student.requestLookTarget(target, 80, 2);

        // 移動：後退
        tryMoveAway(STRAFE_SPEED, 6.0);

        // 発射：interval=4tick（あなたのenum通り）
        if (canSee && shouldFireThisTick(StudentBrAction.DODGE_SHOT)) {
            student.queueFire(target); // メイン
        }
    }

    private void runSideSubShot(boolean right, boolean canSee) {
        // 体：移動方向（AimGoal側 lockBodyYawToMoveDir 前提）
        student.requestLookMoveDir(90, 2);

        // 移動：右/左回り込み
        Vec3d pos = computeStrafePos(target, right, 5.0);
        if (pos != null) {
            mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, STRAFE_SPEED);
        }

        // 発射：interval=2tick
        if (canSee && shouldFireThisTick(right ? StudentBrAction.RIGHT_SIDE_SUB_SHOT : StudentBrAction.LEFT_SIDE_SUB_SHOT)) {
            student.queueFireSub(target);
        }
    }

    private void runSubShot(boolean canSee) {
        mob.getNavigation().stop();
        student.requestLookTarget(target, 80, 2);

        if (canSee && shouldFireThisTick(StudentBrAction.SUB_SHOT)) {
            student.queueFireSub(target);
        }
    }

    private void runMainShot(boolean canSee) {
        mob.getNavigation().stop();
        student.requestLookTarget(target, 80, 2);

        if (canSee && shouldFireThisTick(StudentBrAction.MAIN_SHOT)) {
            student.queueFire(target);
        }
    }

    private void runSubReloadShot(WeaponSpec mainSpec, double dist, boolean canSee) {
        // 視線：敵（静止しつつ牽制）
        student.requestLookTarget(target, 80, 2);

        // 近すぎるときだけ離れる（安全確保）
        if (dist < Math.max(2.5, mainSpec.panicRange)) {
            tryMoveAway(STRAFE_SPEED, 6.0);
        } else {
            mob.getNavigation().stop();
        }

        // サブ牽制：interval=2tick
        if (canSee && shouldFireThisTick(StudentBrAction.SUB_RELOAD_SHOT)) {
            student.queueFireSub(target);
        }

        // ★メイン給弾：reloadTicks と magSize から割り算で「間隔」を作る
        // （1アクションで+1にしたいなら interval=actionTicksLeft とかにすればOK）
        reloadStepCounter++;
        int interval = Math.max(1, mainSpec.reloadTicks / Math.max(1, mainSpec.magSize));
        if (reloadStepCounter % interval == 0) {
            // あなたの実装に合わせて「メイン弾を+1」
            if (student instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
                se.addAmmoInMag(1, mainSpec.magSize); // ★下でメソッド例
            }
        }

        // 本格リロード開始（弾切れで距離安全なら開始）
        if (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0 && !student.isReloading()) {
            if (dist >= mainSpec.panicRange) student.startReload(mainSpec);
        }
    }

    // ★「このtickで撃つべきか」：enumの fireIntervalTicks を使う（揃う）
    private boolean shouldFireThisTick(StudentBrAction a) {
        int interval = Math.max(1, a.fireIntervalTicks);
        // actionAge は 0,1,2... なので、0から数えて interval ごとに撃つ
        return (actionAge % interval) == 0;
    }

    // ===== 移動ユーティリティ =====
    private void tryMoveAway(double speed, double step) {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;

        Vec3d dir = away.normalize();
        Vec3d desired = mob.getPos().add(dir.multiply(step));

        Vec3d pos = FuzzyTargeting.findFrom(mob, 12, 7, desired);
        if (pos == null) pos = desired;

        mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
    }

    private Vec3d computeStrafePos(LivingEntity t, boolean preferRight, double radius) {
        Vec3d center = t.getPos();

        Vec3d fromT = mob.getPos().subtract(center);
        fromT = new Vec3d(fromT.x, 0, fromT.z);
        if (fromT.lengthSquared() < 1.0e-6) return null;

        Vec3d forward = fromT.normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        Vec3d side = preferRight ? right : right.multiply(-1);
        Vec3d desired = center.add(side.multiply(radius));
        desired = desired.add(forward.multiply(-radius * 0.35));

        Vec3d fuzzy = FuzzyTargeting.findFrom(mob, 10, 7, desired);
        return fuzzy != null ? fuzzy : desired;
    }

    // ===== パス到達可否 =====
    private boolean canReach(Vec3d pos) {
        if (pos == null) return false;
        var nav = mob.getNavigation();
        var path = nav.findPathTo(BlockPos.ofFloored(pos), 0);
        return path != null; // まずはこれでOK（厳密にするなら reachesTarget 相当を追加）
    }

    // ===== CD管理 =====
    private void tickCooldowns() {
        for (int i = 0; i < cds.length; i++) {
            if (cds[i] > 0) cds[i]--;
        }
    }

    private void clearAllCds() {
        for (int i = 0; i < cds.length; i++) cds[i] = 0;
    }

    private boolean onCd(StudentBrAction a) {
        return cds[a.ordinal()] > 0;
    }

    private void setCd(StudentBrAction a, int cd) {
        cds[a.ordinal()] = Math.max(cds[a.ordinal()], cd);
    }

    // ===== ターゲット探索 =====
    private LivingEntity findTarget() {
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);
        Box box = mob.getBoundingBox().expand(spec.range + 10.0);

        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && e instanceof HostileEntity
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}