package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;

import java.util.Comparator;
import java.util.EnumSet;

public class StudentEvadeGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private LivingEntity target;

    private int evadeTicks = 0;
    private int stepCooldown = 0;

    // ===== 調整 =====
    private static final int EVADE_DURATION = 10;      // 回避状態の維持tick
    private static final int STEP_COOLDOWN = 6;        // ステップ間隔
    private static final double STEP_DIST = 3;       // ステップ距離（短距離回避）
    private static final double STEP_SPEED = 3;     // 速度（大きいほどシュッ）
    private static final double MAX_DROP = 2.0;        // 戦闘中は2ブロック以上落ちない

    // SECURITY中：警備地点からの最大離脱
    private static final double GUARD_RADIUS = 14.0;

    public StudentEvadeGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE)); // LOOKを外す

    }

    @Override
    public boolean canStart() {
        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        target = findNearestHostile();
        if (target == null) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(target);

        double danger = Math.max(5.0, spec.preferredMinRange);
        return dist < danger;
    }

    @Override
    public boolean shouldContinue() {
        if (evadeTicks > 0) return true;

        if (target == null || !target.isAlive()) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(target);

        return dist < (spec.preferredMinRange + 1.5);
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        evadeTicks = EVADE_DURATION;
        stepCooldown = 0;

        //f (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
          //  se.requestDodge();
        //}
    }

    @Override
    public void stop() {
        evadeTicks = 0;
        stepCooldown = 0;
        target = null;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            if (target == null) return;
        }

        if (evadeTicks > 0) evadeTicks--;
        if (stepCooldown > 0) stepCooldown--;

        // 回避中は敵を見るのを止める→逃げ方向へ向く
        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;
        away = away.normalize();

        // tick() 内：away を作った直後あたりに入れる
        float moveYaw = (float)(Math.toDegrees(Math.atan2(away.z, away.x)) - 90.0);
        mob.setYaw(approachAngle(mob.getYaw(), moveYaw, 30.0f));

// headYaw が触れるなら揃える（触れないならこの行は消す）
        mob.setHeadYaw(mob.getYaw());


        // 逃げ方向（away）へ“体”を向ける（見た目が一番自然）
        mob.setYaw(approachAngle(mob.getYaw(), moveYaw, 30.0f));
        mob.bodyYaw = mob.getYaw();
        mob.headYaw = mob.getYaw();


        // ステップは間隔を置く（連打しすぎると挙動が不安定）
        if (stepCooldown == 0) {
            boolean stepped = tryStep8Dir(away);
            if (stepped) {
                stepCooldown = STEP_COOLDOWN;
            } else {
                // どこにも行けないなら軽く押す（最低限）
                mob.addVelocity(away.x * 0.18, 0.0, away.z * 0.18);
                mob.velocityDirty = true;
            }
        }

        // 段差対策：ぶつかって地上ならジャンプ（1ブロ段差を登りやすく）
        if (mob.horizontalCollision && mob.isOnGround()) {
            mob.getJumpControl().setActive();
            if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
                se.requestJump();
            }
        }
    }

    /**
     * ★8方向（背面基準）で短距離ステップ
     * away（敵から離れる方向）を基準に、
     * 背面/背面斜め/左右/左右斜め/前（最後の手段）を試す
     */
    private boolean tryStep8Dir(Vec3d away) {
        Vec3d right = new Vec3d(-away.z, 0, away.x);

        Vec3d[] dirs = new Vec3d[] {
                away,
                away.add(right).normalize(),
                away.subtract(right).normalize(),
                right,
                right.multiply(-1),
                away.add(right.multiply(0.5)).normalize(),
                away.subtract(right.multiply(0.5)).normalize(),
                away.multiply(-1)
        };

        Vec3d start = mob.getPos();
        var threats = findCloseThreats(4.0); // ★3〜4ブロ

        Vec3d bestDir = null;
        Vec3d bestPos = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (Vec3d d : dirs) {
            Vec3d desired = start.add(d.multiply(STEP_DIST));
            desired = clampToGuardAreaIfNeeded(desired);

            if (!isSafeStepDestination(start, desired)) continue;

            double s = dangerScoreAt(desired, threats);
            if (s < bestScore) {
                bestScore = s;
                bestDir = d;
                bestPos = desired;
            }
        }

        if (bestDir == null) return false;

        // 向きは回避方向（LOOKではなくYawで統一）
        float moveYaw = (float)(Math.toDegrees(Math.atan2(bestDir.z, bestDir.x)) - 90.0);
        mob.setYaw(approachAngle(mob.getYaw(), moveYaw, 45.0f));
        mob.bodyYaw = mob.getYaw();
        mob.headYaw = mob.getYaw();


        // ★シュッとステップ
        mob.setVelocity(bestDir.x * STEP_SPEED, mob.getVelocity().y, bestDir.z * STEP_SPEED);
        mob.velocityDirty = true;

        // ★ここでDODGEアニメ（ステップが発生した瞬間）
        if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
            se.requestDodge();
        }

        mob.getNavigation().startMovingTo(bestPos.x, bestPos.y, bestPos.z, 1.2);
        return true;
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

    /**
     * 目的地が安全か：
     * - 足元がある
     * - 頭上2マス空き
     * - 大きく落ちない（MAX_DROP）
     * - 経路上が壁で塞がってない（簡易：目的地周辺の衝突を見る）
     */
    private boolean isSafeStepDestination(Vec3d from, Vec3d to) {
        var w = mob.getWorld();

        BlockPos bp = BlockPos.ofFloored(to.x, to.y, to.z);

        // 落差チェック（戦闘中に崖落ちしない）
        double dy = from.y - to.y;
        if (dy > MAX_DROP) return false;

        // 足元が固体
        BlockPos below = bp.down();
        if (isDangerousFloor(below)) return false;

        if (w.getBlockState(below).isAir()) return false;
        if (w.getBlockState(below).getCollisionShape(w, below).isEmpty()) return false;

        // 自分の当たり判定（ざっくり2マス）ぶん空いてる
        if (!w.getBlockState(bp).isAir()) return false;
        if (!w.getBlockState(bp.up()).isAir()) return false;

        // 目的地が「登れない壁の中」っぽくないか（軽く周辺確認）
        var shape = w.getBlockState(bp).getCollisionShape(w, bp);
        if (!shape.isEmpty()) return false;

        return true;
    }

    private LivingEntity findNearestHostile() {
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        Box box = mob.getBoundingBox().expand(Math.max(10.0, spec.range));
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && e instanceof HostileEntity
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
    private java.util.List<LivingEntity> findCloseThreats(double r) {
        Box box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                e.isAlive() && e instanceof net.minecraft.entity.mob.HostileEntity
        );
    }
    private double dangerScoreAt(Vec3d pos, java.util.List<LivingEntity> threats) {
        // 小さいほど安全
        double score = 0.0;
        for (LivingEntity e : threats) {
            double d2 = e.squaredDistanceTo(pos);
            // 近いほどペナルティを強く（dが小さいと爆発）
            score += 1.0 / Math.max(0.25, d2);
        }
        return score;
    }
    private boolean isDangerousFloor(BlockPos below) {
        var w = mob.getWorld();
        BlockState st = w.getBlockState(below);

        // マグマブロック
        if (st.isOf(Blocks.MAGMA_BLOCK)) return true;

        // 溶岩（ブロック or 流体）
        if (!w.getFluidState(below).isEmpty() && w.getFluidState(below).isOf(Fluids.LAVA)) return true;

        return false;
    }
    private float approachAngle(float cur, float target, float maxStep) {
        float delta = net.minecraft.util.math.MathHelper.wrapDegrees(target - cur);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return cur + delta;
    }



}
