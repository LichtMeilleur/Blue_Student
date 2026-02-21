package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.*;
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

    private LivingEntity fireTarget;
    private boolean fireIsSub = false;
    private int aimTicks;

    private static final int AIM_TICKS = 1;

    private LookRequest activeLook;

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

        // =========================
        // 0) 回避中は一切触らない
        // =========================
        if (student.isEvading()) {
            fireTarget = null;
            aimTicks = 0;
            activeLook = null;
            return;
        }

        // =========================
        // 1) LookRequest取り込み
        // =========================
        LookRequest incoming = student.consumeLookRequest();
        if (incoming != null) {
            if (activeLook == null || incoming.priority >= activeLook.priority) {
                activeLook = incoming;
            }
        }

        // =========================
        // 2) 射撃キュー取得（sub優先）
        // =========================
        if (fireTarget == null) {

            if (student.hasQueuedFireSub()) {
                LivingEntity t = student.consumeQueuedFireSubTarget();
                if (t != null && t.isAlive()) {
                    fireTarget = t;
                    fireIsSub = true;
                    aimTicks = AIM_TICKS;
                    stopNavigationIfNeeded();
                }
            }

            if (fireTarget == null && student.hasQueuedFire()) {
                LivingEntity t = student.consumeQueuedFireTarget();
                if (t != null && t.isAlive()) {
                    fireTarget = t;
                    fireIsSub = false;
                    aimTicks = AIM_TICKS;
                    stopNavigationIfNeeded();
                }
            }
        }

        // =========================
        // 3) どこを見るか決定
        // =========================
        AimResult aim = null;

        if (activeLook != null) {
            aim = computeAimFromLook(activeLook);
        }

        if (aim == null && fireTarget != null && fireTarget.isAlive()) {
            aim = aimAt(fireTarget.getX(), fireTarget.getEyeY(), fireTarget.getZ());
        }

        if (aim == null) {
            aim = computeAimMoveDir();
        }

        // =========================
        // 4) 適用
        // =========================
        if (aim != null) {
            mob.getLookControl().lookAt(aim.x, aim.y, aim.z, 90f, 90f);

            if (mob instanceof AbstractStudentEntity se) {
                se.setAimAngles(aim.yaw, aim.pitch);

                boolean lockBody = se.shouldLockBodyYawToMoveDir();
                if (!lockBody) {
                    mob.setYaw(approachAngle(mob.getYaw(), aim.yaw, 35f));
                    mob.bodyYaw = mob.getYaw();
                    mob.headYaw = mob.getYaw();
                }
            }
        }

        if (activeLook != null) {
            if (activeLook.holdTicks > 0) activeLook.holdTicks--;
            if (activeLook.holdTicks <= 0) activeLook = null;
        }

        // =========================
        // 5) 実射撃
        // =========================
        if (fireTarget == null) return;

        aimTicks--;
        if (aimTicks > 0) return;

        StudentForm form = StudentForm.NORMAL;
        if (mob instanceof AbstractStudentEntity ase) {
            form = ase.getForm();
        }

        WeaponSpec spec = WeaponSpecs.forStudent(
                student.getStudentId(),
                form,
                fireIsSub
        );

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
        }

        fireTarget = null;
    }

    // ============================================
    // ナビ停止制御（フォーム依存）
    // ============================================
    private void stopNavigationIfNeeded() {

        boolean flying = false;
        if (mob instanceof com.licht_meilleur.blue_student.entity.HinaEntity hina) {
            flying = hina.isFlying();
        }

        boolean stopNav = true;

        if (mob instanceof AbstractStudentEntity se) {
            stopNav = se.shouldStopNavigationForShot(fireIsSub);
        }

        if (!flying && stopNav) {
            mob.getNavigation().stop();
        }
    }

    // ============================================
    // 通常：移動方向を見る
    // ============================================
    private AimResult computeAimMoveDir() {
        Vec3d v = mob.getVelocity();
        Vec3d hv = new Vec3d(v.x, 0, v.z);

        if (hv.lengthSquared() > 1.0e-5) {
            Vec3d p = mob.getPos().add(hv.normalize().multiply(2.0));
            return aimAt(p.x, mob.getEyeY(), p.z);
        }

        if (!mob.getNavigation().isIdle()) {
            Path path = mob.getNavigation().getCurrentPath();
            if (path != null && !path.isFinished()) {
                int idx = path.getCurrentNodeIndex();
                if (idx < path.getLength()) {
                    var nodePos = path.getNodePos(idx);
                    Vec3d p = new Vec3d(
                            nodePos.getX() + 0.5,
                            nodePos.getY() + 0.5,
                            nodePos.getZ() + 0.5
                    );
                    return aimAt(p.x, mob.getEyeY(), p.z);
                }
            }
        }

        return null;
    }

    private AimResult computeAimFromLook(LookRequest r) {
        if (r == null) return null;

        return switch (r.type) {

            case NONE -> null;

            case TARGET -> {
                if (r.target == null || !r.target.isAlive()) yield null;
                yield aimAt(r.target.getX(), r.target.getEyeY(), r.target.getZ());
            }

            case MOVE_DIR -> computeAimMoveDir();

            case POS -> {
                if (r.pos == null) yield null;
                yield aimAt(r.pos.x, r.pos.y, r.pos.z);
            }

            default -> null;
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