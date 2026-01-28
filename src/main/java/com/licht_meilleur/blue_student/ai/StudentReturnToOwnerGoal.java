package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class StudentReturnToOwnerGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;
    private final double speed;
    private final double triggerDist;
    private final double stopDist;

    private PlayerEntity owner;
    private int repathCooldown = 0;

    private static final int REPATH_INTERVAL = 10;
    private static final double TELEPORT_DIST = 36.0; // 6ブロック^2=36ではなく、ここは距離として使うので注意
    private static final double TELEPORT_DIST_SQ = 36.0 * 36.0; // 36ブロック以上でテレポ

    public StudentReturnToOwnerGoal(PathAwareEntity mob, IStudentEntity student, double speed,
                                    double triggerDist, double stopDist) {
        this.mob = mob;
        this.student = student;
        this.speed = speed;
        this.triggerDist = triggerDist;
        this.stopDist = stopDist;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // ★SECURITYでは絶対に帰還しない
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;

        owner = resolveOwner();
        if (owner == null || !owner.isAlive()) return false;

        return mob.squaredDistanceTo(owner) > triggerDist * triggerDist;
    }

    @Override
    public boolean shouldContinue() {
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;
        if (owner == null || !owner.isAlive()) return false;

        return mob.squaredDistanceTo(owner) > stopDist * stopDist;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        owner = null;
    }

    @Override
    public void tick() {
        if (owner == null) return;

        mob.getLookControl().lookAt(owner, 30.0f, 30.0f);

        double dist2 = mob.squaredDistanceTo(owner);

        // 迷子対策：遠すぎたらテレポ
        if (dist2 > TELEPORT_DIST_SQ) {
            tryTeleportNearOwner();
            repathCooldown = 0;
            return;
        }

        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = REPATH_INTERVAL;

        mob.getNavigation().startMovingTo(owner, speed);
    }

    private PlayerEntity resolveOwner() {
        // AbstractStudentEntityに getOwnerPlayer() があるならそれを使うのが一番安全
        if (student instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity se) {
            PlayerEntity p = se.getOwnerPlayer();
            if (p != null) return p;
        }

        // フォールバック
        return mob.getWorld().getClosestPlayer(mob, 32.0);
    }

    private void tryTeleportNearOwner() {
        if (owner == null) return;

        BlockPos base = owner.getBlockPos();

        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (isSafeTeleportPos(p)) {
                        mob.refreshPositionAndAngles(
                                p.getX() + 0.5,
                                p.getY(),
                                p.getZ() + 0.5,
                                mob.getYaw(),
                                mob.getPitch()
                        );
                        mob.getNavigation().stop();
                        return;
                    }
                }
            }
        }
    }

    private boolean isSafeTeleportPos(BlockPos p) {
        var w = mob.getWorld();
        var below = w.getBlockState(p.down());
        if (below.isAir()) return false;

        // 足元が固体で、頭上2マス空き
        if (!below.getCollisionShape(w, p.down()).isEmpty()) {
            return w.getBlockState(p).isAir() && w.getBlockState(p.up()).isAir();
        }
        return false;
    }
}
