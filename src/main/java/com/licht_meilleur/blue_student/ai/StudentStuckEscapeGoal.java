package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
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
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * 詰まり脱出Goal（角・屋内・迷路に強い版）
 *
 * 2段階：
 *  - Phase1: 物理押し出し（数tick）で角から離す（ナビを止める）
 *  - Phase2: 遠めの「開けたアンカー」へナビで抜ける（repath間引き）
 */
public class StudentStuckEscapeGoal extends Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private LivingEntity threat;

    // ====== スタック判定（常時更新） ======
    private Vec3d lastPos = null;
    private int noMoveTicks = 0;

    // ====== 脱出中 ======
    private int escapeTicks = 0;
    private int repathCooldown = 0;

    // ===== 調整（まずはこのまま） =====
    private static final int STUCK_TICKS = 16;          // これ以上動かなければ詰まり
    private static final double MOVE_EPS2 = 0.00035;    // 位置変化がこれ未満なら「動いてない」
    private static final double SPEED_EPS2 = 0.00045;   // 速度がこれ未満なら「動いてない」
    private static final double THREAT_RADIUS = 12.0;   // 近い敵がいるなら戦闘中扱い

    private static final int ESCAPE_DURATION = 40;      // 脱出モード継続tick（少し長め）
    private static final int PUSH_DURATION = 10;        // Phase1：物理押し出しtick
    private static final int REPATH_INTERVAL = 20;      // ナビ更新は間引く（重要）
    private static final double PUSH_SPEED = 0.55;      // Phase1：押し出し速度
    private static final double ANCHOR_SPEED = 1.35;    // Phase2：アンカーへ移動
    private static final double MAX_DROP = 2.0;         // 大落下しない

    // SECURITY制限（あなたの既存方針に合わせる）
    private static final double GUARD_RADIUS = 16.0;

    // 固定の脱出方向（毎tickブレないように）
    private Vec3d fixedDir = null;
    private int fixedDirLock = 0;
    private static final int FIXDIR_LOCK = 25;

    // アンカー（遠めの目的地）
    private Vec3d anchor = null;

    public StudentStuckEscapeGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (mob instanceof AbstractStudentEntity ase && ase.isBrActionActiveServer()) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        // 位置変化
        Vec3d now = mob.getPos();
        double moved2 = 0.0;
        if (lastPos != null) moved2 = now.squaredDistanceTo(lastPos);
        lastPos = now;

        // 速度
        double speed2 = mob.getVelocity().horizontalLengthSquared();
        boolean blocked = mob.horizontalCollision;

        // 近い敵がいるなら戦闘中扱い（戦闘中の詰まりだけ拾う）
        LivingEntity near = findNearestHostile(THREAT_RADIUS);
        boolean inCombat = (near != null);

        boolean notMoving = (moved2 < MOVE_EPS2) || (speed2 < SPEED_EPS2);

        if (inCombat && (blocked || notMoving)) noMoveTicks++;
        else noMoveTicks = 0;

        if (noMoveTicks < STUCK_TICKS) return false;

        threat = near;
        return threat != null;
    }

    @Override
    public boolean shouldContinue() {
        return escapeTicks > 0;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();

        escapeTicks = ESCAPE_DURATION;
        repathCooldown = 0;

        // 固定方向の初期化
        fixedDir = computeEscapeDir();
        fixedDirLock = FIXDIR_LOCK;

        // アンカーは最初は未決定（Phase2で探す）
        anchor = null;

        if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
            se.requestDodge();
        }
    }

    @Override
    public void stop() {
        escapeTicks = 0;
        repathCooldown = 0;

        threat = null;
        anchor = null;

        mob.getNavigation().stop();
        noMoveTicks = 0;
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;

        if (escapeTicks > 0) escapeTicks--;
        if (repathCooldown > 0) repathCooldown--;

        if (threat == null || !threat.isAlive()) {
            threat = findNearestHostile(THREAT_RADIUS);
        }

        // 固定方向（一定tick固定→時々更新）
        if (fixedDirLock > 0) {
            fixedDirLock--;
        } else {
            fixedDir = computeEscapeDir();
            fixedDirLock = FIXDIR_LOCK;
        }

        Vec3d dir = (fixedDir != null) ? fixedDir : new Vec3d(0, 0, -1);

        int elapsed = ESCAPE_DURATION - escapeTicks;

        // =========================
        // Phase1: 物理押し出し（角抜け）
        // =========================
        if (elapsed < PUSH_DURATION) {
            mob.getNavigation().stop();

            Vec3d push = dir.normalize().multiply(PUSH_SPEED);
            mob.setVelocity(push.x, mob.getVelocity().y, push.z);
            mob.velocityDirty = true;

            // 角で引っかかるならジャンプで抜けやすく
            if (mob.horizontalCollision && mob.isOnGround()) {
                mob.getJumpControl().setActive();
                if (mob instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
                    se.requestJump();
                }
            }
            return;
        }

        // =========================
        // Phase2: アンカーへナビ
        // =========================
        if (repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL;

        // アンカーを探す（見つからなければ近めで妥協）
        anchor = findBestAnchor(dir);
        if (anchor == null) {
            // 最低限：今の方向へ5ブロ先を一回試す
            Vec3d fallback = mob.getPos().add(dir.normalize().multiply(5.0));
            fallback = clampToGuardAreaIfNeeded(fallback);
            anchor = fallback;
        }

        // ★pathがnullでも startMovingTo を一度は試す（屋内角でnullが出やすい）
        mob.getNavigation().startMovingTo(anchor.x, anchor.y, anchor.z, ANCHOR_SPEED);
    }

    // =========================================================
    // Anchor / dir
    // =========================================================

    private Vec3d computeEscapeDir() {
        // 基本：敵から離れる
        if (threat != null) {
            Vec3d away = mob.getPos().subtract(threat.getPos());
            away = new Vec3d(away.x, 0, away.z);
            if (away.lengthSquared() > 1e-6) return away.normalize();
        }

        // 次：現在速度方向
        Vec3d v = mob.getVelocity();
        Vec3d hv = new Vec3d(v.x, 0, v.z);
        if (hv.lengthSquared() > 1e-6) return hv.normalize();

        return new Vec3d(0, 0, -1);
    }

    private Vec3d findBestAnchor(Vec3d baseDir) {
        Vec3d start = mob.getPos();
        Vec3d dir = baseDir.normalize();
        Vec3d right = new Vec3d(-dir.z, 0, dir.x);

        // 候補方向（前・斜め・横・後ろ）
        Vec3d[] dirs = new Vec3d[]{
                dir,
                dir.add(right).normalize(),
                dir.subtract(right).normalize(),
                right,
                right.multiply(-1),
                dir.multiply(-1)
        };

        Vec3d best = null;
        double bestScore = -1e18;

        for (Vec3d d : dirs) {
            // 5/7/9ブロ先を試す（角から離す）
            for (int step : new int[]{5, 7, 9}) {
                Vec3d desired = start.add(d.multiply(step));
                desired = clampToGuardAreaIfNeeded(desired);

                // FuzzyTargetingで「歩ける場所」へ寄せる
                Vec3d pos = FuzzyTargeting.findFrom(mob, 14, 7, desired);
                if (pos == null) continue;

                // 落下制限
                if (pos.y < mob.getY() - MAX_DROP) continue;

                if (!isSafeVec(pos)) continue;

                // “開けてる”ほど高評価
                double score = opennessScore(pos);

                // 敵から遠いほど高評価
                if (threat != null) {
                    score += pos.squaredDistanceTo(threat.getPos()) * 0.03;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = pos;
                }
            }
        }

        return best;
    }

    // =========================================================
    // Safety / scoring
    // =========================================================

    private boolean isSafeVec(Vec3d p) {
        BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
        return isWalkableAndSafe(bp);
    }

    private boolean isWalkableAndSafe(BlockPos pos) {
        var w = mob.getWorld();

        BlockPos below = pos.down();

        // 落差（戦闘中は大落下しない）
        if (pos.getY() < mob.getBlockY() - (int) MAX_DROP) return false;

        // 足場
        if (w.getBlockState(below).isAir()) return false;
        if (w.getBlockState(below).getCollisionShape(w, below).isEmpty()) return false;

        // 2マス空き（衝突shapeで判定）
        if (!w.getBlockState(pos).getCollisionShape(w, pos).isEmpty()) return false;
        if (!w.getBlockState(pos.up()).getCollisionShape(w, pos.up()).isEmpty()) return false;

        // 溶岩/マグマ回避（足元）
        if (!w.getFluidState(below).isEmpty() && w.getFluidState(below).isOf(Fluids.LAVA)) return false;
        if (w.getBlockState(below).isOf(Blocks.MAGMA_BLOCK)) return false;

        return true;
    }

    private double opennessScore(Vec3d p) {
        var w = mob.getWorld();
        BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);

        int solid = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos b1 = bp.add(dx, 0, dz);
                BlockPos b2 = bp.add(dx, 1, dz);

                if (!w.getBlockState(b1).getCollisionShape(w, b1).isEmpty()) solid++;
                if (!w.getBlockState(b2).getCollisionShape(w, b2).isEmpty()) solid++;
            }
        }
        // solidが少ないほど良い
        return -solid;
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

    // =========================================================
    // Threat search
    // =========================================================

    private LivingEntity findNearestHostile(double r) {
        Box box = mob.getBoundingBox().expand(r);
        List<LivingEntity> list = mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                e.isAlive() && e instanceof HostileEntity
        );
        return list.stream().min(Comparator.comparingDouble(mob::squaredDistanceTo)).orElse(null);
    }
}
