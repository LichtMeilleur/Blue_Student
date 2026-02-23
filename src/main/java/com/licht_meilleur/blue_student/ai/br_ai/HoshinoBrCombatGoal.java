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

    // ★近距離の“ボス技”レンジ
    private static final double TACKLE_RANGE = 2.2;   // これ以下ならタックル候補
    private static final double BASH_RANGE   = 3.6;   // これ以下ならバッシュ候補

    // ===== 移動速度 =====
    private static final double STRAFE_SPEED = 1.25;

    // ===== リパス頻度（tryMoveAwayで使う）=====
    private static final int REPATH_INTERVAL = 6;
    private int repathCooldown = 0;

    // ===== コマンド式アクション =====
    private StudentBrAction current = StudentBrAction.NONE;
    private int actionTicksLeft = 0;
    private boolean actionStarted = false;     // 開始tickだけ true
    private int actionAge = 0;                // 現アクション開始からの経過tick
    private boolean actionHitDone = false;    // ★タックル/バッシュのヒット1回制御

    // actionごとのCD（ticks）
    private final int[] cds = new int[StudentBrAction.values().length];

    // SUB_RELOAD_SHOT 給弾用
    private int reloadStepCounter = 0;

    // ===== SUB burst control =====
    private int subBurstShots = 0;
    private int subBurstCooldown = 0;

    private static final int SUB_BURST_MAX = 3;        // 3発
    private static final int SUB_BURST_CD_TICKS = 12;  // 0.6秒(好みで)

    // ===== “まとまり”制御 =====
    private StudentBrAction lastAction = StudentBrAction.NONE;
    private int comboLockTicks = 0;       // “次候補縛り” の残り

    // ★MAIN/SUBを一定時間固定して交互を消す
    private enum WeaponMode { MAIN, SUB }
    private WeaponMode mode = WeaponMode.MAIN;
    private int modeLockTicks = 0;


    private int fireCooldown = 0;

    private IStudentEntity.ShotKind burstKind = IStudentEntity.ShotKind.NONE;
    private int burstLeft = 0;

    private int burstShotsRemaining = 0;
    private int burstFireTick = 0;




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

        subBurstShots = 0;
        subBurstCooldown = 0;

        mode = WeaponMode.MAIN;
        modeLockTicks = 0;
        comboLockTicks = 0;
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
        stopAction();

        subBurstShots = 0;
        subBurstCooldown = 0;

        modeLockTicks = 0;
        comboLockTicks = 0;
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;



        tickCooldowns();
        if (fireCooldown > 0) fireCooldown--;
        if (repathCooldown > 0) repathCooldown--;
        if (subBurstCooldown > 0) subBurstCooldown--;
        if (modeLockTicks > 0) modeLockTicks--;
        if (comboLockTicks > 0) comboLockTicks--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            stopAction();
            return;
        }

        WeaponSpec mainSpec = WeaponSpecs.forStudent(student.getStudentId(), StudentForm.BR, false);

        // ★reloadTicksLeft を減らす（BR中のreload管理）
        if (student.isReloading()) {
            student.tickReload(mainSpec);
        }

        double dist = mob.distanceTo(target);
        boolean canSee = mob.getVisibilityCache().canSee(target);

        // ① 実行中アクションがあるなら処理
        if (actionTicksLeft > 0 && current != StudentBrAction.NONE) {
            student.requestBrAction(current, actionTicksLeft);

            runCurrentAction(mainSpec, dist, canSee);

            actionTicksLeft--;
            actionAge++;
            actionStarted = false;

            // ★ここが要点：まだ残ってるならここで終了
            if (actionTicksLeft > 0) return;

            // ★今tickでアクションが終わった → 次アクション選択へ落とす
            // （stopAction() を入れるならここ。入れなくても動くことが多い）
            // stopAction();
        }

        // ② 次アクション選択
        StudentBrAction next = selectNextAction(mainSpec, dist, canSee);

        // ③ duration/cd をここで一括定義（好みで調整）
        switch (next) {
            case GUARD_TACKLE -> startAction(next, 10, 35);
            case GUARD_BASH   -> startAction(next, 10, 25);

            case SUB_SHOT -> startAction(next, 10, 100);
            case RIGHT_SIDE_SUB_SHOT, LEFT_SIDE_SUB_SHOT -> startAction(next, 4, 0);
            case SUB_RELOAD_SHOT -> startAction(next, 4, 0);

            case MAIN_SHOT -> startAction(next, 6, 0);
            case DODGE_SHOT -> startAction(next, 6, 10);

            case IDLE, NONE -> startAction(StudentBrAction.IDLE, 10, 0); // ★明示

            default -> startAction(StudentBrAction.IDLE, 10, 0);
        }
    }

    // ===== 次アクション選択 =====
    private StudentBrAction selectNextAction(WeaponSpec mainSpec, double dist, boolean canSee) {


        /*
        if (burstLeft > 0) {
            burstLeft--;
            if (burstKind == IStudentEntity.ShotKind.SUB) return StudentBrAction.SUB_SHOT;
            if (burstKind == IStudentEntity.ShotKind.MAIN) return StudentBrAction.MAIN_SHOT;
        }
        */

        // ★「タックル/バッシュ後」は追撃ルートに寄せる（ボスっぽい）
        // comboLockTicks は startAction/runAction 側で入れる
        if (comboLockTicks > 0) {
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 16);
            if (!onCd(StudentBrAction.SUB_SHOT)) return StudentBrAction.SUB_SHOT;
            return StudentBrAction.SUB_RELOAD_SHOT;
        }

        // ★リロード中/メイン弾切れはサブ寄り
        if (student.isReloading()) {
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 20);
            return StudentBrAction.SUB_RELOAD_SHOT;
        }
        if (!mainSpec.infiniteAmmo && student.getAmmoInMag() <= 0) {
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 30);
            if (dist <= DODGE_SHOT_DIST && !onCd(StudentBrAction.DODGE_SHOT)) return StudentBrAction.DODGE_SHOT;
            return StudentBrAction.SUB_RELOAD_SHOT;
        }

        // ★近距離：まずは“ボス技”優先（見えてる時だけ）
        if (canSee) {
            if (dist <= TACKLE_RANGE && !onCd(StudentBrAction.GUARD_TACKLE)) {
                // タックル後は追撃サブに寄せたい
                mode = WeaponMode.SUB;
                modeLockTicks = Math.max(modeLockTicks, 20);
                return StudentBrAction.GUARD_TACKLE;
            }
            if (dist <= BASH_RANGE && !onCd(StudentBrAction.GUARD_BASH)) {
                mode = WeaponMode.SUB;
                modeLockTicks = Math.max(modeLockTicks, 16);
                return StudentBrAction.GUARD_BASH;
            }
            if (!canSee) return StudentBrAction.IDLE;
        }

        // 近距離：DODGE優先（これ中はSUB扱いに寄せる）
        if (dist <= DODGE_SHOT_DIST && !onCd(StudentBrAction.DODGE_SHOT)) {
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 16);
            return StudentBrAction.DODGE_SHOT;
        }

        // SIDE（空いてる側）…これはSUB系なのでSUBに寄せて固定
        if (canSee && dist >= SIDE_SHOT_MIN && dist <= SIDE_SHOT_MAX) {
            StudentBrAction side = chooseSideAction();
            if (side != StudentBrAction.NONE && !onCd(side)) {
                mode = WeaponMode.SUB;
                modeLockTicks = Math.max(modeLockTicks, 16);
                return side;
            }
        }

        if (modeLockTicks > 0) {
            if (mode == WeaponMode.SUB) {
                if (!canFireSubNow()) return StudentBrAction.IDLE;

                // ★ここを追加：CD中は撃たない
                if (onCd(StudentBrAction.SUB_SHOT)) return StudentBrAction.IDLE;

                return StudentBrAction.SUB_SHOT;
            }

            if (onCd(StudentBrAction.MAIN_SHOT)) return StudentBrAction.IDLE;
            return StudentBrAction.MAIN_SHOT;
        }

        if (!canSee) return StudentBrAction.IDLE;

// 遠すぎるなら「撃たない」じゃなく「寄る」行動へ
        if (dist > SIDE_SHOT_MAX + 2.0) {
            return StudentBrAction.IDLE; // ここでMOVEは別ロジックで
        }

        return StudentBrAction.SUB_SHOT; // 最後は必ず何か
    }

    private StudentBrAction chooseSideAction() {
        Vec3d rp = computeStrafePos(target, true, 5.0);
        Vec3d lp = computeStrafePos(target, false, 5.0);



        boolean rOk = canReach(rp);
        boolean lOk = canReach(lp);

        if (rOk && !lOk) return StudentBrAction.RIGHT_SIDE_SUB_SHOT;
        if (!rOk && lOk) return StudentBrAction.LEFT_SIDE_SUB_SHOT;

        if (rOk) return StudentBrAction.RIGHT_SIDE_SUB_SHOT;
        return StudentBrAction.NONE;
    }

    // ===== アクション開始/停止 =====
    private void startAction(StudentBrAction a, int duration, int cooldown) {
        current = a;
        actionTicksLeft = Math.max(1, duration);
        actionStarted = true;
        actionAge = 0;
        actionHitDone = false;

        // ★ここが要点：アクション開始時にバースト設定
        burstShotsRemaining = switch (a) {
            case SUB_SHOT  ->3;// サブ3連
            case RIGHT_SIDE_SUB_SHOT, LEFT_SIDE_SUB_SHOT -> 1;
            case MAIN_SHOT, DODGE_SHOT -> 2; // メイン2連
            default -> 0;
        };
        burstFireTick = 0;

        student.requestBrAction(a, actionTicksLeft);
        if (cooldown > 0) setCd(a, cooldown);
    }

    private void stopAction() {
        current = StudentBrAction.NONE;
        actionTicksLeft = 0;
        actionStarted = false;
        actionAge = 0;
        actionHitDone = false;
        reloadStepCounter = 0;

    }

    // ===== アクション実行 =====
    private void runCurrentAction(WeaponSpec mainSpec, double dist, boolean canSee) {
        switch (current) {
            case GUARD_TACKLE -> runTackle(canSee);
            case GUARD_BASH   -> runBash(canSee);

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

    // ====== 近接：タックル / バッシュ ======

    private void runTackle(boolean canSee) {
        student.requestLookTarget(target, 90, 2);

        if (actionStarted) {
            Vec3d to = dirTowardTarget();
            dash(to, 1.05); // 強め突進
        }

        // ヒット判定：1回だけ
        if (!actionHitDone && canSee && isTouchingTarget(1.0)) {
            actionHitDone = true;

            // ダメージ + 強ノックバック
            float dmg = 5.0f;
            target.damage(mob.getDamageSources().mobAttack(mob), dmg);

            Vec3d dir = dirTowardTarget();
            if (dir != null) {
                // ノックバック：相手を“押し出す”ので逆向き
                target.takeKnockback(1.2, -dir.x, -dir.z);
            }

            // ★追撃ルート：しばらくサブだけに絞る
            comboLockTicks = 30;
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 20);
        }
    }

    private void runBash(boolean canSee) {
        student.requestLookTarget(target, 90, 2);

        if (actionStarted) {
            Vec3d to = dirTowardTarget();
            dash(to, 0.85); // ちょい短め
        }

        if (!actionHitDone && canSee && isTouchingTarget(0.9)) {
            actionHitDone = true;

            float dmg = 3.5f;
            target.damage(mob.getDamageSources().mobAttack(mob), dmg);

            Vec3d dir = dirTowardTarget();
            if (dir != null) {
                target.takeKnockback(0.95, -dir.x, -dir.z);
            }

            comboLockTicks = 24;
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 16);
        }
    }

    private boolean isTouchingTarget(double expand) {
        if (target == null) return false;
        Box a = mob.getBoundingBox().expand(expand);
        Box b = target.getBoundingBox();
        return a.intersects(b);
    }

    private Vec3d dirTowardTarget() {
        if (target == null) return null;
        Vec3d d = target.getPos().subtract(mob.getPos());
        d = new Vec3d(d.x, 0, d.z);
        if (d.lengthSquared() < 1.0e-6) return null;
        return d.normalize();
    }

    // ====== 既存射撃アクション ======

    private void runDodgeShot(WeaponSpec mainSpec, boolean canSee) {
        student.requestLookTarget(target, 80, 2);

        if (actionStarted) {
            Vec3d away = dirAwayFromTarget();
            dash(away, 0.85);
        }

        boolean fired = tryFireOnce(false, StudentBrAction.DODGE_SHOT, canSee);
        if (fired) actionTicksLeft = 0;
    }

    private void runSideSubShot(boolean rightSide, boolean canSee) {
        Vec3d d = dirSideAroundTarget(rightSide);

        if (d != null) {
            // ★移動先（ストレイフ方向）を見る
            float yaw = (float)(Math.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0f;
            mob.setYaw(yaw);
            mob.bodyYaw = yaw;
            mob.headYaw = yaw;
        }

        if (actionStarted) {
            dash(d, 1.0);
        } else {
            mob.getNavigation().stop();
        }

        StudentBrAction a = rightSide ? StudentBrAction.RIGHT_SIDE_SUB_SHOT : StudentBrAction.LEFT_SIDE_SUB_SHOT;

        boolean fired = tryFireOnce(true, a, canSee);
        if (fired) actionTicksLeft = 0; // ★B案：撃ったらこのアクション終了
    }

    private void runSubShot(boolean canSee) {
        mob.getNavigation().stop();
        student.requestLookTarget(target, 80, 2);
        if (!canSee) return;

        if (burstShotsRemaining <= 0) { actionTicksLeft = 0; return; }

        // ★ここを fireCooldown に統一
        if (fireCooldown > 0) return;

        student.queueFireSub(target);
        onFiredSub();

        burstShotsRemaining--;

        // 次の発射間隔
        boolean fired = tryFireOnce(true, StudentBrAction.SUB_SHOT, canSee);
        if (!fired) return;

        burstShotsRemaining--;
        burstFireTick = Math.max(1, StudentBrAction.SUB_SHOT.fireIntervalTicks) - 1;

        if (burstShotsRemaining <= 0) actionTicksLeft = 0;
    }

    private void runMainShot(boolean canSee) {
        mob.getNavigation().stop();
        student.requestLookTarget(target, 80, 2);

        if (!canSee) return;
        if (burstShotsRemaining <= 0) { actionTicksLeft = 0; return; }

        // ★ここを fireCooldown に統一
        if (fireCooldown > 0) return;

        student.queueFireSub(target);
        onFiredSub();

        burstShotsRemaining--;

        // 次の発射間隔
        fireCooldown = Math.max(1, StudentBrAction.MAIN_SHOT.fireIntervalTicks);

        if (burstShotsRemaining <= 0) actionTicksLeft = 0;
    }

    private void runSubReloadShot(WeaponSpec mainSpec, double dist, boolean canSee) {
        student.requestLookTarget(target, 80, 2);

        if (dist < Math.max(2.5, mainSpec.panicRange)) {
            tryMoveAway(STRAFE_SPEED, 6.0);
        } else {
            mob.getNavigation().stop();
        }

        boolean fired = tryFireOnce(true, StudentBrAction.SUB_RELOAD_SHOT, canSee);
        if (fired) actionTicksLeft = 0;

        // 給弾はそのままでOK（アクション短いので interval 調整が必要なら後で）
        reloadStepCounter++;
        int interval = Math.max(1, mainSpec.reloadTicks / Math.max(1, mainSpec.magSize));
        if (reloadStepCounter % interval == 0) {
            if (student instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
                se.addAmmoInMag(1, mainSpec.magSize);
            }
        }
    }


    private boolean tryFireOnce(boolean sub, StudentBrAction a, boolean canSee) {
        if (!canSee) return false;
        if (fireCooldown > 0) return false;

        // subバースト制限（暴発防止）
        if (sub && !canFireSubNow()) return false;

        if (sub) {
            student.queueFireSub(target);
            onFiredSub();
        } else {
            student.queueFire(target);
        }

        if (sub) {
            mode = WeaponMode.SUB;
            modeLockTicks = Math.max(modeLockTicks, 10);
        } else {
            mode = WeaponMode.MAIN;
            modeLockTicks = Math.max(modeLockTicks, 10);
        }

        if (sub) {
            burstKind = IStudentEntity.ShotKind.SUB;
            burstLeft = 3; // 3発はSUB固定
        } else {
            burstKind = IStudentEntity.ShotKind.MAIN;
            burstLeft = 2; // 2発はMAIN固定
        }
        // ★次の発射までの間隔は action の fireIntervalTicks を使う
        fireCooldown = Math.max(1, a.fireIntervalTicks);
        return true;
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

    private boolean canReach(Vec3d pos) {
        if (pos == null) return false;
        var nav = mob.getNavigation();
        var path = nav.findPathTo(BlockPos.ofFloored(pos), 0);
        return path != null;
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
        int idx = a.ordinal();
        return idx >= 0 && idx < cds.length && cds[idx] > 0;
    }

    private void setCd(StudentBrAction a, int cd) {
        int idx = a.ordinal();
        if (idx < 0 || idx >= cds.length) return;
        cds[idx] = Math.max(cds[idx], cd);
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

    private boolean canFireSubNow() {
        return subBurstCooldown <= 0;
    }

    private void onFiredSub() {
        subBurstShots++;
        if (subBurstShots >= SUB_BURST_MAX) {
            subBurstShots = 0;
            subBurstCooldown = SUB_BURST_CD_TICKS;
        }
    }

    // ===== Dash util =====
    private void dash(Vec3d dir, double horizSpeed) {
        if (dir == null) return;
        Vec3d d = new Vec3d(dir.x, 0, dir.z);
        if (d.lengthSquared() < 1.0e-6) return;
        d = d.normalize().multiply(horizSpeed);

        mob.getNavigation().stop();
        mob.setVelocity(d.x, mob.getVelocity().y, d.z); // y維持
        mob.velocityDirty = true;
    }

    private Vec3d dirAwayFromTarget() {
        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return null;
        return away.normalize();
    }

    /**
     * ★side_shot左右が逆だったので反転版
     * forward × up の符号を反転して “右” を逆にしている
     */
    private Vec3d dirSideAroundTarget(boolean rightSide) {
        Vec3d forward = mob.getRotationVec(1.0f);
        forward = new Vec3d(forward.x, 0, forward.z);
        if (forward.lengthSquared() < 1.0e-6) return null;
        forward = forward.normalize();

        // ★ここを反転：右 = (z, 0, -x)
        Vec3d right = new Vec3d(forward.z, 0, -forward.x);

        Vec3d side = rightSide ? right : right.multiply(-1);

        // 横 + ちょい前進（見た目がステップっぽい）
        Vec3d toward = forward.multiply(0.15);
        Vec3d out = side.add(toward);

        if (out.lengthSquared() < 1.0e-6) return side.normalize();
        return out.normalize();
    }
}