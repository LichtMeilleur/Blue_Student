package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class StudentFollowGoal extends net.minecraft.entity.ai.goal.Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;
    private final double speed;

    private PlayerEntity owner;

    // パス再計算間隔
    private int repathCooldown = 0;

    // ここは好みで調整
    private static final double STOP_DIST = 2.0;          // 2m以内は止まる
    private static final double TELEPORT_DIST = 16.0;     // 16m以上離れたらワープ検討
    private static final int REPATH_INTERVAL = 10;        // 10tickに1回

    public StudentFollowGoal(PathAwareEntity mob, IStudentEntity student, double speed) {
        this.mob = mob;
        this.student = student;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;
        owner = resolveOwner();
        return owner != null && owner.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;
        if (owner == null || !owner.isAlive()) return false;
        return mob.squaredDistanceTo(owner) > (STOP_DIST * STOP_DIST);
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

        // 視線
        mob.getLookControl().lookAt(owner, 30.0f, 30.0f);

        double dist2 = mob.squaredDistanceTo(owner);

        // 近いなら止める
        if (dist2 < (STOP_DIST * STOP_DIST)) {
            mob.getNavigation().stop();
            return;
        }

        // 遠すぎるならワープ（段差/迷子対策）
        if (dist2 > (TELEPORT_DIST * TELEPORT_DIST)) {
            tryTeleportNearOwner();
            repathCooldown = 0;
            return;
        }

        // パス再計算を間引く
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = REPATH_INTERVAL;

        mob.getNavigation().startMovingTo(owner, speed);
    }

    private PlayerEntity resolveOwner() {
        // サーバーで動くGoalなので ServerWorld で取れることが多い
        if (mob.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            var uuid = student.getOwnerUuid();
            if (uuid != null) {
                PlayerEntity p = sw.getPlayerByUuid(uuid);
                if (p != null) return p;
            }
        }
        // フォールバック
        return mob.getWorld().getClosestPlayer(mob, 16.0);
    }

    private void tryTeleportNearOwner() {
        if (owner == null) return;
        // ownerの近くの安全位置へ
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
        // 足元が固体で、頭上2マスが空いてる
        var below = w.getBlockState(p.down());
        if (below.isAir()) return false;
        if (!below.getCollisionShape(w, p.down()).isEmpty()) {
            return w.getBlockState(p).isAir() && w.getBlockState(p.up()).isAir();
        }
        return false;
    }
}
