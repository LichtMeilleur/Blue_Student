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
    private IStudentEntity.FireChannel fireChannel = IStudentEntity.FireChannel.MAIN;
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
// 2) 射撃キュー取得（チャンネル）
// =========================
        if (fireTarget == null) {

            // ---- BRは「欲しいチャンネルだけ消費」＆DODGE中はMAINのみ ----
            if (mob instanceof AbstractStudentEntity ase && ase.getForm() == StudentForm.BR) {

                StudentBrAction a = ase.getBrActionServer();
                boolean dodge = (a == StudentBrAction.DODGE_SHOT);

                IStudentEntity.FireChannel desired = IStudentEntity.FireChannel.MAIN;

                if (!dodge && a != null && a.shotKind == IStudentEntity.ShotKind.SUB) {
                    // いまはSUB=SUB_L扱い（AliceでSUB_Rを足すのは後）
                    desired = IStudentEntity.FireChannel.SUB_L;
                }

                LivingEntity t = null;

                if (dodge) {
                    // DODGE中はMAINのみ
                    if (student.hasQueuedFire(IStudentEntity.FireChannel.MAIN)) {
                        t = student.consumeQueuedFireTarget(IStudentEntity.FireChannel.MAIN);
                    }
                    if (t != null && t.isAlive()) {
                        fireTarget = t;
                        fireChannel = IStudentEntity.FireChannel.MAIN;
                        aimTicks = 0; // BR即発射
                        stopNavigationIfNeeded();
                    }
                } else {
                    // BRは「欲しい方だけ」消費（反対を勝手に食わない）
                    if (student.hasQueuedFire(desired)) {
                        t = student.consumeQueuedFireTarget(desired);
                    }

                    if (t != null && t.isAlive()) {
                        fireTarget = t;
                        fireChannel = desired;
                        aimTicks = 0; // BR即発射
                        stopNavigationIfNeeded();
                    }
                }

                // 欲しい方が無いなら何もしない

            } else {
                // ---- 通常フォーム：SUB優先→MAIN（好みで順序変更可） ----
                LivingEntity t = null;

                if (student.hasQueuedFire(IStudentEntity.FireChannel.SUB_L)) {
                    t = student.consumeQueuedFireTarget(IStudentEntity.FireChannel.SUB_L);
                    if (t != null && t.isAlive()) {
                        fireTarget = t;
                        fireChannel = IStudentEntity.FireChannel.SUB_L;
                        aimTicks = AIM_TICKS;
                        stopNavigationIfNeeded();
                    }
                } else if (student.hasQueuedFire(IStudentEntity.FireChannel.SUB_R)) {
                    t = student.consumeQueuedFireTarget(IStudentEntity.FireChannel.SUB_R);
                    if (t != null && t.isAlive()) {
                        fireTarget = t;
                        fireChannel = IStudentEntity.FireChannel.SUB_R;
                        aimTicks = AIM_TICKS;
                        stopNavigationIfNeeded();
                    }
                } else if (student.hasQueuedFire(IStudentEntity.FireChannel.MAIN)) {
                    t = student.consumeQueuedFireTarget(IStudentEntity.FireChannel.MAIN);
                    if (t != null && t.isAlive()) {
                        fireTarget = t;
                        fireChannel = IStudentEntity.FireChannel.MAIN;
                        aimTicks = AIM_TICKS;
                        stopNavigationIfNeeded();
                    }
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



        if (mob.age % 10 == 0 && mob instanceof AbstractStudentEntity ase && ase.getForm() == StudentForm.BR) {
            System.out.println("[AIM-BR] hasSub=" + student.hasQueuedFireSub()
                    + " hasMain=" + student.hasQueuedFire()
                    + " fireTarget=" + (fireTarget != null)
                    + " aimTicks=" + aimTicks
                    + " brAction=" + ase.getBrActionServer());
        }


        // 6) 実射撃
        if (fireTarget == null) return;

        aimTicks--;
        if (aimTicks > 0) return;

// form確定
        StudentForm form = StudentForm.NORMAL;
        if (mob instanceof AbstractStudentEntity ase) {
            form = ase.getForm();
        }
// ch はこの少し上で定義してる fireChannel を使う
        final IStudentEntity.FireChannel ch = fireChannel;
        final boolean isSubShot = (ch != IStudentEntity.FireChannel.MAIN);




// ★ここを修正
        final WeaponSpec spec =
                WeaponSpecs.forStudent(student.getStudentId(), form, fireChannel);

// 射程＆視界チェック
        double dist = mob.distanceTo(fireTarget);
        boolean canSee = mob.getVisibilityCache().canSee(fireTarget);
        if (!canSee || dist > spec.range) {
            fireTarget = null;
            return;
        }

// 顔向け
        if (mob instanceof AbstractStudentEntity se) {
            se.faceTargetForShot(fireTarget, 35f, 25f);
        }

// 発射

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

            student.requestShot(
                    isSubShot ? IStudentEntity.ShotKind.SUB : IStudentEntity.ShotKind.MAIN,
                    fireTarget
            );
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
            boolean isSub = (fireChannel != IStudentEntity.FireChannel.MAIN);
            stopNav = se.shouldStopNavigationForShot(isSub);
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