package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.UUID;

public class StudentFollowGoal extends Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;
    private final double speed;

    private PlayerEntity owner;

    // ===== Waypoints（生徒側）=====
    private final Deque<Vec3d> waypoints = new ArrayDeque<>();
    private static final int MAX_WAYPOINTS = 40;          // 20秒分くらい（0.5秒間隔想定）
    private static final int RECORD_INTERVAL = 10;        // 10tick=0.5秒
    private static final double RECORD_MIN_DIST = 2.0;    // 2ブロ以上動いたら記録

    private int recordCooldown = 0;

    // ===== 追従制御 =====
    private int repathCooldown = 0;
    private static final int REPATH_INTERVAL = 10;

    private static final double STOP_DIST = 2.0;

    // ===== スタック検知→巻き戻し/最終ワープ =====
    private Vec3d lastPos = null;
    private int stuckTicks = 0;
    private static final double MOVE_EPS2 = 0.00035;
    private static final int STUCK_THRESHOLD = 25;       // 1.25秒くらい
    private static final int WARP_STUCK_THRESHOLD = 80;  // 4秒くらい
    private static final double WARP_DIST = 24.0;        // 24m以上離れてて詰むならワープOK

    public StudentFollowGoal(PathAwareEntity mob, IStudentEntity student, double speed) {
        this.mob = mob;
        this.student = student;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;
        owner = resolveOwnerOnly();
        return owner != null && owner.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        if (student.getAiMode() != StudentAiMode.FOLLOW) return false;
        if (owner == null || !owner.isAlive()) return false;

        UUID uuid = student.getOwnerUuid();
        if (uuid == null || !uuid.equals(owner.getUuid())) return false;

        return mob.squaredDistanceTo(owner) > (STOP_DIST * STOP_DIST);
    }

    @Override
    public void start() {
        repathCooldown = 0;
        recordCooldown = 0;
        stuckTicks = 0;
        lastPos = mob.getPos();
        waypoints.clear();

        // スタート時に現在位置を入れておく（安全）
        waypoints.addLast(owner.getPos());
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        owner = null;
        waypoints.clear();
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // ownerUuidが無くなったら停止
        UUID uuid = student.getOwnerUuid();
        if (uuid == null || !uuid.equals(owner.getUuid())) {
            mob.getNavigation().stop();
            owner = null;
            waypoints.clear();
            return;
        }

        // ===== ownerの足跡を記録（一定間隔＆動いた時だけ）=====
        if (recordCooldown > 0) recordCooldown--;
        if (recordCooldown == 0) {
            recordCooldown = RECORD_INTERVAL;
            recordOwnerWaypoint();
        }

        // ===== スタック検知 =====
        updateStuck();

        // 近いなら止める
        double dist2 = mob.squaredDistanceTo(owner);
        if (dist2 < STOP_DIST * STOP_DIST) {
            mob.getNavigation().stop();
            stuckTicks = 0;
            return;
        }

        // ★スタックしてるなら waypoint を巻き戻して別経路を試す
        if (stuckTicks > STUCK_THRESHOLD) {
            rewindWaypoints();
            stuckTicks = 0;
        }

        // ★完全に詰んでて距離が遠いなら最終ワープ（壁で完全遮断のときだけ）
        if (stuckTicks > WARP_STUCK_THRESHOLD && dist2 > (WARP_DIST * WARP_DIST)) {
            tryTeleportNearOwner();
            stuckTicks = 0;
            repathCooldown = 0;
            return;
        }

        // パス再計算を間引く
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = REPATH_INTERVAL;



        // ===== 目的地は「owner直行」ではなく waypoint を使う =====
        Vec3d goal = getFollowGoalPosition();

// ★ここが重要：そのgoalにパスが作れないなら waypoint を捨てて次へ
        var path = mob.getNavigation().findPathTo(goal.x, goal.y, goal.z, 0);
        if (path == null) {
            // 先頭がダメなら捨てる（数回繰り返す）
            int drop = 0;
            while (drop < 4 && !waypoints.isEmpty()) {
                waypoints.pollFirst();
                drop++;
                Vec3d g2 = getFollowGoalPosition();
                if (g2 == null) break;
                path = mob.getNavigation().findPathTo(g2.x, g2.y, g2.z, 0);
                if (path != null) {
                    goal = g2;
                    break;
                }
            }
        }
        if (path == null) {
            // 全滅なら owner直行に戻す
            goal = owner.getPos();
        }


        mob.getNavigation().startMovingTo(goal.x, goal.y, goal.z, speed);

        // 視線：基本は移動方向を向く（見た目が自然）
        lookMoveDirectionOrOwner();
    }

    private void recordOwnerWaypoint() {
        Vec3d p = owner.getPos();

        // 直前と近すぎるなら追加しない
        Vec3d last = waypoints.peekLast();
        if (last != null && last.squaredDistanceTo(p) < (RECORD_MIN_DIST * RECORD_MIN_DIST)) return;

        waypoints.addLast(p);
        while (waypoints.size() > MAX_WAYPOINTS) {
            waypoints.pollFirst();
        }
    }

    private Vec3d getFollowGoalPosition() {
        // waypointが空なら ownerへ
        Vec3d head = waypoints.peekFirst();
        if (head == null) return owner.getPos();

        // 先頭 waypoint に十分近づいたら消す（次へ）
        double d2 = mob.squaredDistanceTo(head.x, head.y, head.z);
        if (d2 < 3.0 * 3.0) {
            waypoints.pollFirst();
            Vec3d next = waypoints.peekFirst();
            return (next != null) ? next : owner.getPos();
        }

        return head;
    }

    private void rewindWaypoints() {
        // waypointを少し巻き戻して “別の曲がり” を試す
        // 先頭を捨てるだけでも効果が出る
        if (!waypoints.isEmpty()) waypoints.pollFirst();
        if (!waypoints.isEmpty()) waypoints.pollFirst(); // 2個落として変化を強める（好みで）
    }

    private void updateStuck() {
        Vec3d now = mob.getPos();
        if (lastPos != null) {
            double moved2 = now.squaredDistanceTo(lastPos);
            double speed2 = mob.getVelocity().horizontalLengthSquared();
            boolean blocked = mob.horizontalCollision;
            boolean notMoving = (moved2 < MOVE_EPS2) || (speed2 < 0.0005);

            if (mob.getNavigation().isFollowingPath() && (blocked || notMoving)) stuckTicks++;
            else stuckTicks = 0;
        }
        lastPos = now;
    }

    private void lookMoveDirectionOrOwner() {
        Vec3d v = mob.getVelocity();
        Vec3d hv = new Vec3d(v.x, 0, v.z);
        if (hv.lengthSquared() > 1.0e-4) {

            return;
        }
        // 動いてない時だけオーナーを見る
        student.requestLookMoveDir(10, 2);
    }

    private PlayerEntity resolveOwnerOnly() {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return null;
        UUID uuid = student.getOwnerUuid();
        if (uuid == null) return null;
        PlayerEntity p = sw.getPlayerByUuid(uuid);
        return (p != null && p.isAlive()) ? p : null;
    }

    private void tryTeleportNearOwner() {
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
        if (!below.getCollisionShape(w, p.down()).isEmpty()) {
            return w.getBlockState(p).isAir() && w.getBlockState(p.up()).isAir();
        }
        return false;
    }
}
