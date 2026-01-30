package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class StudentGetAirGoal extends Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private BlockPos targetPos;
    private int repathCooldown = 0;

    // 調整
    private static final int REPATH_INTERVAL = 10;
    private static final int SEARCH_H = 3;      // 上下探索
    private static final int SEARCH_R = 10;     // 半径探索
    private static final double SPEED = 1.35;

    public StudentGetAirGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE)); // LOOKは奪わない
    }

    @Override
    public boolean canStart() {
        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        // ここが「水対策」の核
        if (!mob.isSubmergedInWater()) return false;

        // 余裕あるときは発火しない（見た目自然）
        // ※ getAir() は「現在の空気」、getMaxAir() は最大
        if (mob.getAir() > mob.getMaxAir() * 0.6) return false;

        targetPos = findNearestDrySpot();
        return targetPos != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!mob.isSubmergedInWater()) return false;
        if (targetPos == null) return false;

        // 近づいたら継続不要
        return mob.squaredDistanceTo(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5
        ) > 2.0;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetPos = null;
    }

    @Override
    public void tick() {
        if (repathCooldown > 0) {
            repathCooldown--;
        } else {
            repathCooldown = REPATH_INTERVAL;

            // 途中で状況変わるので再検索してもOK（軽い）
            BlockPos p = findNearestDrySpot();
            if (p != null) targetPos = p;

            if (targetPos != null) {
                mob.getNavigation().startMovingTo(
                        targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, SPEED
                );
            }
        }

        // ナビが詰む/見つからない時の最低限：浮上
        if (targetPos == null || mob.getNavigation().isIdle()) {
            Vec3d v = mob.getVelocity();
            mob.setVelocity(v.x, Math.max(v.y, 0.08), v.z);
            mob.velocityDirty = true;
        }
    }

    private BlockPos findNearestDrySpot() {
        BlockPos base = mob.getBlockPos();
        var w = mob.getWorld();

        BlockPos best = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (int dy = -1; dy <= SEARCH_H; dy++) {
            for (int dx = -SEARCH_R; dx <= SEARCH_R; dx++) {
                for (int dz = -SEARCH_R; dz <= SEARCH_R; dz++) {
                    BlockPos p = base.add(dx, dy, dz);

                    // 足場と空間
                    if (!isWalkableAndBreathable(p)) continue;

                    double d2 = p.getSquaredDistance(base);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private boolean isWalkableAndBreathable(BlockPos pos) {
        var w = mob.getWorld();

        BlockPos below = pos.down();
        BlockState belowSt = w.getBlockState(below);

        // 足場がある
        if (belowSt.isAir()) return false;
        if (belowSt.getCollisionShape(w, below).isEmpty()) return false;

        // 2マス空き
        if (!w.getBlockState(pos).getCollisionShape(w, pos).isEmpty()) return false;
        if (!w.getBlockState(pos.up()).getCollisionShape(w, pos.up()).isEmpty()) return false;

        // そこが水（呼吸できない）ならダメ
        if (w.getBlockState(pos).isOf(Blocks.WATER) || w.getBlockState(pos.up()).isOf(Blocks.WATER)) return false;

        return true;
    }
}
