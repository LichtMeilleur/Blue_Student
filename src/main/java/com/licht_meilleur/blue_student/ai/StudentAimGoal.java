package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.LookRequest;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class StudentAimGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private final WeaponAction projectileAction = new ProjectileWeaponAction();
    private final WeaponAction hitscanAction = new HitscanWeaponAction();
    private final WeaponAction shotgunHitscanAction = new ShotgunHitscanWeaponAction();

    // ===== fire =====
    private LivingEntity fireTarget;
    private int aimTicks;
    private static final int AIM_TICKS = 1;

    // ===== look request (hold対応) =====
    private LookRequest activeLook;

    private Float lastMoveYaw = null;


    public StudentAimGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        StudentAiMode mode = student.getAiMode();
        return mode == StudentAiMode.FOLLOW || mode == StudentAiMode.SECURITY;
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        // ★回避中は Aim が一切 LOOK を触らない（EvadeGoalに任せる）
        if (student.isEvading()) {
            fireTarget = null;
            aimTicks = 0;
            activeLook = null;
            return;
        }

        // 1) LookRequest の取り込み（最優先で使う）
        LookRequest incoming = student.consumeLookRequest();
        if (incoming != null) {
            if (activeLook == null || incoming.priority >= activeLook.priority) {
                activeLook = incoming;
            }
        }

        // 2) 射撃要求（キュー）を拾う
        if (fireTarget == null && student.hasQueuedFire()) {
            LivingEntity t = student.consumeQueuedFireTarget();
            if (t != null && t.isAlive()) {
                fireTarget = t;
                aimTicks = AIM_TICKS;

                boolean flying = (mob instanceof com.licht_meilleur.blue_student.entity.HinaEntity hina) && hina.isFlying();
                if (!flying) mob.getNavigation().stop();
            } else {
                fireTarget = null;
                aimTicks = 0;
            }
        }

        // 3) どこを見るか決定（敵を見る優先）
        AimResult aim = null;

        // ★最優先：LookRequest（TARGET/AWAY_FROM など）
        aim = computeAimFromLook(activeLook);

        // ★次：射撃中なら敵を見る
        if (aim == null && fireTarget != null && fireTarget.isAlive()) {
            aim = aimAt(fireTarget.getX(), fireTarget.getEyeY(), fireTarget.getZ());
        }

        // ★最後：移動方向
        if (aim == null) {
            aim = computeAimMoveDir();
        }

        // 4) 適用
        if (aim != null) {
            mob.getLookControl().lookAt(aim.x, aim.y, aim.z, 90.0f, 90.0f);

            if (mob instanceof AbstractStudentEntity se) {
                se.setAimAngles(aim.yaw, aim.pitch);
            }

            mob.setYaw(approachAngle(mob.getYaw(), aim.yaw, 35.0f));
            mob.bodyYaw = mob.getYaw();
            mob.headYaw = mob.getYaw();
        }

        // 5) holdTicks 減算
        if (activeLook != null) {
            if (activeLook.holdTicks > 0) activeLook.holdTicks--;
            if (activeLook.holdTicks <= 0) activeLook = null;
        }

        // 6) 実射撃
        if (fireTarget == null) return;

        aimTicks--;
        if (aimTicks > 0) return;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        if (mob instanceof AbstractStudentEntity se) {
            se.faceTargetForShot(fireTarget, 35f, 25f);
        }

        boolean fired;
        if (spec.fxType == WeaponSpec.FxType.SHOTGUN) {
            fired = shotgunHitscanAction.shoot(student, fireTarget, spec);
        } else {
            fired = switch (spec.type) {
                case PROJECTILE -> projectileAction.shoot(student, fireTarget, spec);
                case HITSCAN -> hitscanAction.shoot(student, fireTarget, spec);
            };
        }

        if (fired) {
            student.requestShot();
            if (!spec.infiniteAmmo) student.consumeAmmo(1);

            if (mob instanceof com.licht_meilleur.blue_student.entity.HinaEntity hina) {
                hina.beginFlyShotPulse(2);
            }
        }

        fireTarget = null;
    }


    // =========================
    // 通常：移動方向を見る
    // =========================
    private AimResult computeAimMoveDir() {
        // 1) velocity があるなら velocity
        Vec3d v = mob.getVelocity();
        Vec3d hv = new Vec3d(v.x, 0, v.z);
        if (hv.lengthSquared() > 1.0e-5) {
            Vec3d p = mob.getPos().add(hv.normalize().multiply(2.0));
            return aimAt(p.x, mob.getEyeY(), p.z);
        }

        // 2) velocity が小さいがナビが動いてるなら path の次ノード
        if (!mob.getNavigation().isIdle()) {
            Path path = mob.getNavigation().getCurrentPath();
            if (path != null && !path.isFinished()) {
                int idx = path.getCurrentNodeIndex();
                // 次ノードが取れそうならそっちを向く（詰まりでも “進みたい方向” を向きやすい）
                if (idx < path.getLength()) {
                    var nodePos = path.getNodePos(idx);
                    Vec3d p = new Vec3d(nodePos.getX() + 0.5, nodePos.getY() + 0.5, nodePos.getZ() + 0.5);
                    return aimAt(p.x, mob.getEyeY(), p.z);
                }
            }
        }

        return null;
    }

    // =========================
    // LookRequest 由来
    // =========================
    private AimResult computeAimFromLook(LookRequest r) {
        if (r == null) return null;

        return switch (r.type) {

            case NONE -> null;

            case TARGET -> {
                LivingEntity t = r.target;
                if (t == null || !t.isAlive()) yield null;
                yield aimAt(t.getX(), t.getEyeY(), t.getZ());
            }
            case AWAY_FROM -> {
                LivingEntity t = r.target;
                if (t == null || !t.isAlive()) yield null;

                Vec3d away = mob.getPos().subtract(t.getPos());
                away = new Vec3d(away.x, 0, away.z);
                if (away.lengthSquared() < 1e-6) yield null;

                Vec3d p = mob.getPos().add(away.normalize().multiply(2.0));
                yield aimAt(p.x, mob.getEyeY(), p.z);
            }
            case WORLD_DIR -> {
                Vec3d d = r.dir;
                if (d == null) yield null;

                Vec3d dd = new Vec3d(d.x, 0, d.z);
                if (dd.lengthSquared() < 1e-6) yield null;

                Vec3d p = mob.getPos().add(dd.normalize().multiply(2.0));
                yield aimAt(p.x, mob.getEyeY(), p.z);
            }
            case MOVE_DIR -> computeAimMoveDir();

            case POS -> {
                Vec3d p = r.pos;
                if (p == null) yield null;
                yield aimAt(p.x, mob.getEyeY(), p.z);
            }

        };
    }

    private AimResult aimAt(double x, double y, double z) {
        double dx = x - mob.getX();
        double dz = z - mob.getZ();
        double dy = y - mob.getEyeY();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new AimResult(x, y, z, yaw, pitch);
    }

    private float approachAngle(float cur, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - cur);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return cur + delta;
    }

    private record AimResult(double x, double y, double z, float yaw, float pitch) {}
}
