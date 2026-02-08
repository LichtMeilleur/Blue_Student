package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

public class StudentCombatGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private final WeaponAction projectileAction = new ProjectileWeaponAction();
    private final WeaponAction hitscanAction = new HitscanWeaponAction();

    private int cooldown = 0;
    private LivingEntity target;

    private static final double COMBAT_CHASE_SPEED = 1.35;
    private static final double COMBAT_AIM_SPEED = 1.10;

    private static final int REPATH_INTERVAL = 16;
    private int repathCooldown = 0;

    // SECURITY中：警備地点からの最大離脱距離
    private static final double GUARD_RADIUS = 16.0;

    private int noActionTicks = 0;
    private Vec3d lastPos = Vec3d.ZERO;

    private static final int FORCE_FIRE_TICKS = 10; // 20〜30で好み
    private static final double STILL_EPS2 = 0.0003; // 動いてない判定


    public StudentCombatGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        target = findTarget();


        if (target != null) {
            mob.setTarget(target); // ★追加：他のシステム(ドローン等)が owner.getTarget() で取れるようにする
        }
        return target != null;


    }

    @Override
    public boolean shouldContinue() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        if (target == null || !target.isAlive()) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double keep = spec.range + 8.0;
        return mob.squaredDistanceTo(target) <= keep * keep;
    }

    @Override
    public void start() {
        //cooldown = 0f;
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        boolean flying = (mob instanceof com.licht_meilleur.blue_student.entity.HinaEntity hina) && hina.isFlying();

        if (!(mob.getWorld() instanceof ServerWorld)) return;

        if (cooldown > 0) cooldown -= 1;
        if (repathCooldown > 0) repathCooldown--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            mob.getNavigation().stop();
            return;
        }

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(target);

        // ===== 追う（理想距離へ）=====
        // ===== 追う（理想距離へ）=====
        if (!flying) {
            if (dist > spec.preferredMaxRange || dist > spec.range) {
                tryMoveTowardTarget(COMBAT_CHASE_SPEED, spec);
                return;
            }

            if (!mob.getVisibilityCache().canSee(target)) {
                tryMoveTowardTarget(COMBAT_AIM_SPEED, spec);
                return;
            }

            if (dist < spec.preferredMinRange) {
                mob.getNavigation().stop();
                return;
            }
        }

// ★飛行中は「位置取りはHinaStrafeFlyGoalに任せる」
// ここから下（リロード/射撃キュー）は今まで通り


        // ===== 見えない：角度変えるために軽く寄る =====
        if (!mob.getVisibilityCache().canSee(target)) {
            tryMoveTowardTarget(COMBAT_AIM_SPEED, spec);

            return;
        }

        // ===== 近すぎ：EvadeGoalが動くのでCombatは止まるだけ =====
        if (dist < spec.preferredMinRange) {
            mob.getNavigation().stop();
            return;
        }

        // =========================================================
        // ★ここから：リロード/残弾ロジック（この位置が正解）
        // =========================================================

        // ① リロード中なら「撃たない＆敵を見ない＆移動だけ」
        if (student.isReloading()) {
            student.tickReload(spec);

            // リロード中はパニック距離なら下がる/位置調整（簡易）
            if (dist < spec.panicRange) {
                tryMoveAwayFromTarget(COMBAT_CHASE_SPEED, spec);

            } else {
                // 近すぎなければ停止して落ち着いてリロード
                mob.getNavigation().stop();

            }
            return;
        }

        // ② 残弾ゼロなら「距離が安全ならリロード、危険なら下がる」
        if (student.getAmmoInMag() <= 0 && !spec.infiniteAmmo) {
            if (dist < spec.panicRange) {
                tryMoveAwayFromTarget(COMBAT_CHASE_SPEED, spec);

            } else {
                student.startReload(spec);
                mob.getNavigation().stop();
            }
            return;
        }

        // ③ 早めリロード（例：残弾が reloadStartAmmo 以下）
        if (!spec.infiniteAmmo && student.getAmmoInMag() <= spec.reloadStartAmmo) {
            // 近いならまず距離を取ってから
            if (dist < spec.panicRange) {
                tryMoveAwayFromTarget(COMBAT_CHASE_SPEED, spec);

            } else {
                student.startReload(spec);
                mob.getNavigation().stop();
            }
            return;
        }

// =========================================================
// ★射撃（照準は毎tick維持）
// =========================================================

// まず照準だけは維持（cooldown中でもOK）
        student.requestLookTarget(target, 50, 2);

// 飛行してないなら足を止める（射撃の安定）
        if (!flying) mob.getNavigation().stop();

// ★クールダウン中は「キューを積まない」
        if (cooldown > 0) return;

// ★撃ちたい意思だけキュー（ここだけ！）
        student.queueFire(target);

// ★連射間隔はCombat側で管理
        cooldown = spec.cooldownTicks;


        return;




    }



    private void tryMoveTowardTarget(double speed, WeaponSpec spec) {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        // 目的地を「敵そのもの」ではなく、理想距離付近に置くとグネりにくい
        Vec3d to = target.getPos().subtract(mob.getPos());
        to = new Vec3d(to.x, 0, to.z);
        if (to.lengthSquared() < 1.0e-6) return;

        Vec3d dir = to.normalize();
        double want = Math.max(spec.preferredMinRange + 0.5, Math.min(spec.preferredMaxRange, spec.range - 0.5));
        Vec3d desired = target.getPos().subtract(dir.multiply(want));

        // SECURITY中は警備地点から離れすぎない
        desired = clampToGuardAreaIfNeeded(desired);

        mob.getNavigation().startMovingTo(desired.x, desired.y, desired.z, speed);
    }

    private Vec3d clampToGuardAreaIfNeeded(Vec3d desired) {
        if (student.getAiMode() != StudentAiMode.SECURITY) return desired;

        BlockPos guard = student.getSecurityPos();
        if (guard == null) return desired;

        Vec3d center = new Vec3d(guard.getX() + 0.5, guard.getY() + 0.5, guard.getZ() + 0.5);
        Vec3d v = desired.subtract(center);

        double r = GUARD_RADIUS;
        if (v.lengthSquared() > r * r) {
            Vec3d clamped = center.add(v.normalize().multiply(r));
            return new Vec3d(clamped.x, desired.y, clamped.z);
        }
        return desired;
    }

    private LivingEntity findTarget() {
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        Box box = mob.getBoundingBox().expand(spec.range + 8.0);
        LivingEntity found = mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && e instanceof HostileEntity
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);

        // SECURITY中は「警備地点から遠すぎる敵」を無視してもいい（拠点防衛感UP）
        if (found != null && student.getAiMode() == StudentAiMode.SECURITY) {
            BlockPos guard = student.getSecurityPos();
            if (guard != null) {
                double d2 = found.squaredDistanceTo(guard.getX() + 0.5, guard.getY() + 0.5, guard.getZ() + 0.5);
                double r = GUARD_RADIUS + 6.0; // 少し余裕
                if (d2 > r * r) return null;
            }
        }

        return found;
    }

    private void tryMoveAwayFromTarget(double speed, WeaponSpec spec) {
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;

        Vec3d dir = away.normalize();

        // 逃げ先を少し先に（壁で詰むならFuzzyが補正してくれる）
        Vec3d desired = mob.getPos().add(dir.multiply(6.0));
        desired = clampToGuardAreaIfNeeded(desired);

        Vec3d pos = FuzzyTargeting.findFrom(mob, 12, 7, desired);
        if (pos == null) pos = desired;

        mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, speed);
    }

    private void tryTriggerSkill(WeaponSpec spec, double dist) {
        if (!(mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se)) return;
        if (!se.canStartSkill()) return;

        // 戦闘中に「たまに」発動（確率 or 条件）
        // 例：10秒に1回チャンス（200tickに1回）
        if (mob.age % 200 != 0) return;

        // 例：条件（キャラ別にここで分けず、handler側のshouldStartでもOK）
        se.startSkillNow();
    }




}
