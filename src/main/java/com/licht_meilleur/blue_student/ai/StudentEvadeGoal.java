package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class StudentEvadeGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private LivingEntity target;

    private int evadeTicks = 0;
    private int stepCooldown = 0;

    // ===== 調整 =====
    private static final int EVADE_DURATION = 10;
    private static final int STEP_COOLDOWN = 60;
    private static final double STEP_DIST = 3;
    private static final double STEP_SPEED = 3;
    private static final double MAX_DROP = 2.0;

    // ★回避Goal自体の再発動クールダウン（連発防止）
    private static final int EVADE_GLOBAL_COOLDOWN = 60; // 3秒。好みで 10〜40
    private int lastEvadeStartAge = -999999;

    // SECURITY中：警備地点からの最大離脱
    private static final double GUARD_RADIUS = 14.0;

    public StudentEvadeGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;

        // MOVEだけ握る（LOOKはAimGoalに一任）
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        StudentAiMode mode = student.getAiMode();
        if (mob instanceof AbstractStudentEntity ase && ase.isBrActionActiveServer()) return false;
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        // ★連発防止
        if (mob.age - lastEvadeStartAge < EVADE_GLOBAL_COOLDOWN) return false;

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
        student.setEvading(true);

        /*com.licht_meilleur.blue_student.util.DebugChat.near(
                mob, 32,
                "[Evade] START age=" + mob.age + " lastStart=" + lastEvadeStartAge
        );*/

    }

    @Override
    public void stop() {
        evadeTicks = 0;
        stepCooldown = 0;
        target = null;
        student.setEvading(false);

        /*com.licht_meilleur.blue_student.util.DebugChat.near(
                mob, 32,
                "[Evade] STOP age=" + mob.age + " setCooldownBase=" + mob.age
        );*/

        // ★「終わった時刻」でクールタイム開始
        lastEvadeStartAge = mob.age;
    }

   /* @Override
    public void tick() {

        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            if (target == null) return;
        }

        if (evadeTicks > 0) evadeTicks--;
        if (stepCooldown > 0) stepCooldown--;

        // 回避方向（敵から離れる）
        Vec3d away = mob.getPos().subtract(target.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;
        away = away.normalize();

        // ★向きは AimGoal に任せる：回避中は MOVE_DIR を見る（敵を見ない）
        // 逃げ方向へ向ける（このtickで確実に向く）
        float moveYaw = (float)(Math.toDegrees(Math.atan2(away.z, away.x)) - 90.0);
        mob.setYaw(moveYaw);
        mob.bodyYaw = moveYaw;
        mob.headYaw = moveYaw;

// 視線も逃げ方向へ（見た目用）
        Vec3d p = mob.getPos().add(away.multiply(2.0));
        mob.getLookControl().lookAt(p.x, mob.getEyeY(), p.z, 90.0f, 90.0f);



        // ステップ
        if (stepCooldown == 0) {
            boolean stepped = tryStep8Dir(away);
            if (stepped) {
                stepCooldown = STEP_COOLDOWN;
            } else {
                mob.addVelocity(away.x * 0.18, 0.0, away.z * 0.18);
                mob.velocityDirty = true;
            }
        }

        // 段差対策ジャンプ
        if (mob.horizontalCollision && mob.isOnGround()) {
            mob.getJumpControl().setActive();
            if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
                se.requestJump();
            }
        }
    }*/
   @Override
   public void tick() {
       if (evadeTicks > 0) evadeTicks--;
       if (stepCooldown > 0) stepCooldown--;

       // ★向きはここではいじらない（上書きが疑わしいので）
       // まずは「ステップできるか」だけテストしたい

       if (stepCooldown == 0) {
           boolean stepped = tryStep8DirFreeSpace(); // ★敵参照なし版
           if (stepped) {
               stepCooldown = STEP_COOLDOWN;
           }
       }

       if (mob.horizontalCollision && mob.isOnGround()) {
           mob.getJumpControl().setActive();
           if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
               se.requestJump();
           }
       }
   }
    private boolean tryStep8DirFreeSpace() {
        // ★基準は「今向いている方向」
        float yaw = mob.getYaw(); // bodyYawでもOK
        Vec3d forward = yawToDir(yaw);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        // ★優先順：後ろ/後ろ斜め/横/前は最後（好みで調整）
        Vec3d[] dirs = new Vec3d[] {
                forward.multiply(-1), // 後ろ
                forward.multiply(-1).add(right).normalize(),
                forward.multiply(-1).subtract(right).normalize(),
                right,
                right.multiply(-1),
                forward.multiply(-0.5).add(right).normalize(),
                forward.multiply(-0.5).subtract(right).normalize(),
                forward // 前（最後の手段）
        };

        Vec3d start = mob.getPos();

        Vec3d bestDir = null;
        Vec3d bestPos = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Vec3d d : dirs) {
            Vec3d desired = start.add(d.multiply(STEP_DIST));
            desired = clampToGuardAreaIfNeeded(desired);

            if (!isSafeStepDestination(start, desired)) continue;

            // ★評価：障害物が少ない方向を優先（前方レイでスコア）
            double open = opennessScore(start, d, 3.0); // 3ブロ先まで
            // ★微妙に「今の向きに近い方向」を優先したいなら加点
            double align = d.dotProduct(forward.multiply(-1)); // 後ろ基準なら forward(-1) と一致で加点

            double score = open * 2.0 + align * 0.2;

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
                bestPos = desired;
            }
        }

        if (bestDir == null || bestPos == null) return false;

        // ★ステップ速度
        mob.setVelocity(bestDir.x * STEP_SPEED, mob.getVelocity().y, bestDir.z * STEP_SPEED);
        mob.velocityDirty = true;

        if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
            se.requestDodge();
        }

        mob.getNavigation().startMovingTo(bestPos.x, bestPos.y, bestPos.z, 1.2);
        return true;
    }

    private Vec3d yawToDir(float yawDeg) {
        // Minecraft yaw: 0=South, 90=West, -90=East, 180/-180=North
        double rad = Math.toRadians(yawDeg);
        double x = -Math.sin(rad);
        double z =  Math.cos(rad);
        return new Vec3d(x, 0, z).normalize();
    }

    /**
     * 方向dに対して「どれだけ開けているか」簡易スコア
     * rayDistance ブロック分、0.5刻みで衝突ブロックがあったら減点。
     */
    private double opennessScore(Vec3d start, Vec3d dir, double rayDistance) {
        var w = mob.getWorld();
        double score = 0.0;

        Vec3d d = new Vec3d(dir.x, 0, dir.z);
        if (d.lengthSquared() < 1e-6) return 0.0;
        d = d.normalize();

        // 0.5ブロ刻み
        for (double t = 0.5; t <= rayDistance; t += 0.5) {
            Vec3d p = start.add(d.multiply(t));
            BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);

            // 足元/頭上が詰まってたら減点
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) score -= 2.0;
            if (!w.getBlockState(bp.up()).getCollisionShape(w, bp.up()).isEmpty()) score -= 2.0;

            // 何もなければ加点
            if (w.getBlockState(bp).isAir() && w.getBlockState(bp.up()).isAir()) score += 1.0;
        }

        return score;
    }



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
        List<LivingEntity> threats = findCloseThreats(4.0);

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

        // ★ここで確定。無ければ終了
        if (bestDir == null || bestPos == null) return false;

        // ★回避中の向きは EvadeGoal 側で確定させる（AimGoalは止まる想定）
        float moveYaw = (float)(Math.toDegrees(Math.atan2(bestDir.z, bestDir.x)) - 90.0);
        mob.setYaw(moveYaw);
        mob.bodyYaw = moveYaw;
        mob.headYaw = moveYaw;

        // 視線も回避方向へ（見た目用）
        Vec3d p = mob.getPos().add(bestDir.multiply(2.0));
        mob.getLookControl().lookAt(p.x, mob.getEyeY(), p.z, 90.0f, 90.0f);

        // ★ステップ速度
        mob.setVelocity(bestDir.x * STEP_SPEED, mob.getVelocity().y, bestDir.z * STEP_SPEED);
        mob.velocityDirty = true;

        // dodgeアニメ（ここは「1回だけ」にしたいならフラグ制御）
        if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
            se.requestDodge();
        }

        // ナビも一応
        mob.getNavigation().startMovingTo(bestPos.x, bestPos.y, bestPos.z, 1.2);
        return true;
    }


    // ===== helpers =====

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

    private boolean isSafeStepDestination(Vec3d from, Vec3d to) {
        var w = mob.getWorld();
        BlockPos bp = BlockPos.ofFloored(to.x, to.y, to.z);

        double dy = from.y - to.y;
        if (dy > MAX_DROP) return false;

        BlockPos below = bp.down();
        if (isDangerousFloor(below)) return false;

        if (w.getBlockState(below).isAir()) return false;
        if (w.getBlockState(below).getCollisionShape(w, below).isEmpty()) return false;

        if (!w.getBlockState(bp).isAir()) return false;
        if (!w.getBlockState(bp.up()).isAir()) return false;

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

    private List<LivingEntity> findCloseThreats(double r) {
        Box box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                e.isAlive() && e instanceof HostileEntity
        );
    }

    private double dangerScoreAt(Vec3d pos, List<LivingEntity> threats) {
        double score = 0.0;
        for (LivingEntity e : threats) {
            double d2 = e.squaredDistanceTo(pos);
            score += 1.0 / Math.max(0.25, d2);
        }
        return score;
    }

    private boolean isDangerousFloor(BlockPos below) {
        var w = mob.getWorld();
        BlockState st = w.getBlockState(below);

        if (st.isOf(Blocks.MAGMA_BLOCK)) return true;
        if (!w.getFluidState(below).isEmpty() && w.getFluidState(below).isOf(Fluids.LAVA)) return true;

        return false;
    }
}
