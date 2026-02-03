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

    private float cooldown = 0f;
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
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        target = findTarget();
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
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;

        if (cooldown > 0) cooldown -= 1f;
        if (repathCooldown > 0) repathCooldown--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.getNavigation().stop();
            return;
        }



        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double distH = Math.sqrt(dx*dx + dz*dz);      // 水平距離
        double dist3 = mob.distanceTo(target);        // 3D距離（必要なら）
        double dist = isFlyingMob() ? distH : dist3;  // ★飛行中は水平で見る


        // ===== 追う（理想距離へ）=====
        if (dist > spec.preferredMaxRange || dist > spec.range) {
            if (isFlyingMob()) {
                // ★飛行中：地上ナビで追わない（撃てる距離になるまで少し寄るだけ）
                mob.getNavigation().stop();

                Vec3d to = target.getPos().subtract(mob.getPos());
                Vec3d flat = new Vec3d(to.x, 0, to.z);
                if (flat.lengthSquared() > 1e-6) {
                    Vec3d dir = flat.normalize().multiply(0.06); // 寄る強さ：0.03〜0.10で調整
                    mob.addVelocity(dir.x, 0, dir.z);
                    mob.velocityDirty = true;
                }

                // 向きは敵へ
                mob.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ(), 70f, 70f);
                return;
            }

            // 地上は今まで通り
            tryMoveTowardTarget(COMBAT_CHASE_SPEED, spec);
            lookMoveDirection();
            return;
        }


        // ===== 見えない：角度変えるために軽く寄る =====
        if (!mob.getVisibilityCache().canSee(target)) {
            tryMoveTowardTarget(COMBAT_AIM_SPEED, spec);
            lookMoveDirection();
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
                lookMoveDirection();
            } else {
                // 近すぎなければ停止して落ち着いてリロード
                mob.getNavigation().stop();
                lookMoveDirection(); // 進行方向を向く（見た目用）
            }
            return;
        }

        // ② 残弾ゼロなら「距離が安全ならリロード、危険なら下がる」
        if (student.getAmmoInMag() <= 0 && !spec.infiniteAmmo) {
            if (dist < spec.panicRange) {
                tryMoveAwayFromTarget(COMBAT_CHASE_SPEED, spec);
                lookMoveDirection();
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
                lookMoveDirection();
            } else {
                student.startReload(spec);
                mob.getNavigation().stop();
            }
            return;
        }

        // =========================================================
// ★射撃（照準は毎tick維持）
// =========================================================
        mob.getNavigation().stop();

// 目線を狙う（自然）
        double tx = target.getX();
        double ty = target.getEyeY();
        double tz = target.getZ();

// 追従の速さ：左右/上下（ここが“向くスピード”）
        float yawSpeed = 70.0f;   // 30→70くらいが体感良いこと多い
        float pitchSpeed = 70.0f;

        mob.getLookControl().lookAt(tx, ty, tz, yawSpeed, pitchSpeed);

// ここでは実射撃しない（AimFireGoalに任せる）
        if (cooldown > 0) return;

// ★撃ちたい意思だけキュー
        student.queueFire(target);

// 連射間隔はCombat側で管理（今まで通り）
        cooldown = spec.cooldownTicks;


        boolean inRange = dist <= spec.range;
        boolean standing =
                mob.getNavigation().isIdle() &&
                        mob.getVelocity().horizontalLengthSquared() < 0.0002 &&
                        mob.getPos().squaredDistanceTo(lastPos) < STILL_EPS2;

        lastPos = mob.getPos();

// 「撃った／回避した／追いかけた」など、行動したならここで noActionTicks をリセットしたい
        boolean didSomethingThisTick = false;

// 例：あなたのコードで fired が取れるならそれでOK
// didSomethingThisTick |= fired;

// 例：ナビが動いてたら「行動中」とみなす
        if (!mob.getNavigation().isIdle()) didSomethingThisTick = true;

// 例：速度があるなら移動中
        if (mob.getVelocity().horizontalLengthSquared() > 0.002) didSomethingThisTick = true;

        if (inRange && standing && !didSomethingThisTick) {
            noActionTicks++;
        } else {
            noActionTicks = 0;
        }

// ★一定tick棒立ちなら “強制攻撃”
        if (noActionTicks >= FORCE_FIRE_TICKS) {
            noActionTicks = 0;

            // ★クールダウン中は何もしない
            if (cooldown > 0) return;

            mob.getNavigation().stop();
            mob.getLookControl().lookAt(target, 200.0f, 200.0f); // 速めでOK

            // A案：キュー方式なら
            student.queueFire(target);
            //student.requestShot(target); // モーションだけ先に出したいなら

            // B案：ここで実射撃してしまうなら（今のWeaponActionを呼ぶ）
            // boolean fired2 = switch (spec.type) {...};
            // if (fired2) cooldown = spec.cooldownTicks;
        }

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
    private void lookMoveDirection() {
        Vec3d v = mob.getVelocity();
        Vec3d hv = new Vec3d(v.x, 0, v.z);
        if (hv.lengthSquared() < 1.0e-4) return;

        Vec3d p = mob.getPos().add(hv.normalize().multiply(2.0));
        mob.getLookControl().lookAt(p.x, mob.getEyeY(), p.z, 60.0f, 60.0f);
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

    private boolean isFlyingMob() {
        return (mob instanceof com.licht_meilleur.blue_student.entity.HinaEntity hina) && hina.isFlying();
    }




}
