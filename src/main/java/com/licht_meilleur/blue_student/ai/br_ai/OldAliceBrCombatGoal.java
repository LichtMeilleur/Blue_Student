package com.licht_meilleur.blue_student.ai.br_ai;

import com.licht_meilleur.blue_student.entity.AliceEntity;
import com.licht_meilleur.blue_student.entity.projectile.HyperCannonEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentBrAction;
import com.licht_meilleur.blue_student.student.StudentForm;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.EnumSet;

public class OldAliceBrCombatGoal extends Goal {
    private final PathAwareEntity mob;
    private final AliceEntity alice;

    private LivingEntity target;

    // ---- orbit ----
    private int orbitDir = 1;
    private int orbitSwitchTicks = 0;
    private double orbitRadius = 7.5;

    // ---- flight ----
    private static final double HIGH_ABOVE = 6.0;
    private static final double LOW_ABOVE  = 1.2;
    private static final double AGL_MIN = 1.5;
    private static final double AGL_MAX = 10.0;

    private static final double ORBIT_SPEED = 0.35;
    private static final double ALT_SPEED   = 0.28;

    // ---- fire ----
    private int cdMain = 0;
    private int cdHyper = 0;

    // ---- hyper state ----
    private int hyperSetTicks = 0;
    private int hyperBeamTicks = 0;

    // 「spawn済みか」を持つ（Entity自身が追従するので参照保持しなくてOK）
    private boolean beamsSpawned = false;

    public OldAliceBrCombatGoal(PathAwareEntity mob, AliceEntity alice) {
        this.mob = mob;
        this.alice = alice;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private boolean isBr() {
        return alice.getForm() == StudentForm.BR;
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (!isBr()) return false;
        if (alice.isLifeLockedForGoal()) return false;

        target = findTarget();
        if (target != null) mob.setTarget(target);
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (!isBr()) return false;
        if (alice.isLifeLockedForGoal()) return false;
        if (target == null || !target.isAlive()) return false;

        double keep = 28.0;
        return mob.squaredDistanceTo(target) <= keep * keep;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        orbitSwitchTicks = 0;
        cdMain = 0;
        cdHyper = 0;
        hyperSetTicks = 0;
        hyperBeamTicks = 0;
        beamsSpawned = false;
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
        beamsSpawned = false;
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return;

        if (cdMain > 0) cdMain--;
        if (cdHyper > 0) cdHyper--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            beamsSpawned = false;
            return;
        }

        boolean canSee = mob.getVisibilityCache().canSee(target);

        tickOrbitRandom();
        tickOrbitMoveAndAltitude(sw, canSee);

        // 見た目：常にターゲット
        alice.requestLookTarget(target, 80, 2);
        mob.getLookControl().lookAt(target, 90.0f, 90.0f);

        // ハイパー中
        if (hyperSetTicks > 0 || hyperBeamTicks > 0) {
            tickHyperCannon(sw);
            return;
        }

        // キャノン開始条件
        double dist = mob.distanceTo(target);
        if (cdHyper <= 0 && canSee && dist <= 18.0) {
            startHyperCannon();
            return;
        }

        // 通常射撃
        if (cdMain <= 0 && canSee) {
            alice.requestBrAction(orbitDir > 0 ? StudentBrAction.RIGHT_MOVE_SHOT : StudentBrAction.LEFT_MOVE_SHOT, 6);
            alice.queueFire(target, IStudentEntity.FireChannel.MAIN);
            cdMain = 6;
        } else {
            alice.requestBrAction(orbitDir > 0 ? StudentBrAction.RIGHT_MOVE : StudentBrAction.LEFT_MOVE, 6);
        }
    }

    private void tickOrbitRandom() {
        if (orbitSwitchTicks > 0) { orbitSwitchTicks--; return; }
        orbitSwitchTicks = 40 + mob.getRandom().nextInt(60);
        orbitDir = mob.getRandom().nextBoolean() ? 1 : -1;
        orbitRadius = 6.5 + mob.getRandom().nextDouble() * 2.5;
    }

    private void tickOrbitMoveAndAltitude(ServerWorld sw, boolean canSee) {
        Vec3d my = mob.getPos();
        Vec3d tp = target.getPos();

        Vec3d to = tp.subtract(my);
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        if (flat.lengthSquared() < 1e-6) flat = new Vec3d(1,0,0);
        Vec3d radial = flat.normalize();

        Vec3d tangent = (orbitDir > 0)
                ? new Vec3d(-radial.z, 0, radial.x)
                : new Vec3d(radial.z, 0, -radial.x);

        double d = Math.sqrt(flat.lengthSquared());
        double radialPush = (d - orbitRadius) * 0.09;
        Vec3d velXZ = tangent.multiply(ORBIT_SPEED).add(radial.multiply(radialPush));

        // 前方壁チェック（最低限）
        Vec3d forward = velXZ.lengthSquared() < 1e-6 ? mob.getRotationVec(1.0f) : velXZ.normalize();
        Vec3d checkPos = my.add(forward.multiply(1.6));

        BlockHitResult frontHit = sw.raycast(new RaycastContext(
                my, checkPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mob
        ));
        boolean frontBlocked = (frontHit.getType() == HitResult.Type.BLOCK);

        if (frontBlocked) {
            orbitDir *= -1;
            orbitRadius += 1.2;
            velXZ = radial.multiply(-0.45);
        }

        double desiredY = pickClearanceAltitude(sw, my, tp, canSee);

        double agl = estimateGroundDistance(sw, my);
        if (agl < AGL_MIN) desiredY += (AGL_MIN - agl);
        if (agl > AGL_MAX) desiredY -= Math.min(agl - AGL_MAX, 1.0);

        double dy = desiredY - my.y;
        double velY = clamp(dy * 0.15, -ALT_SPEED, ALT_SPEED);

        if (frontBlocked) velY = Math.max(velY, 0.22);

        mob.getNavigation().stop();
        mob.setNoGravity(true);
        mob.setVelocity(velXZ.x, velY, velXZ.z);
        mob.velocityDirty = true;
        mob.fallDistance = 0;
        mob.setOnGround(false);
    }

    private double pickClearanceAltitude(ServerWorld sw, Vec3d my, Vec3d tp, boolean canSee) {
        double base = tp.y + (canSee ? HIGH_ABOVE : LOW_ABOVE);

        // まず低い高度から試して、通らなければ上げる
        for (int i = 0; i <= 10; i++) {
            double y = base + i * 0.8; // 0.8刻みで上げる
            Vec3d from = new Vec3d(my.x, y, my.z);
            Vec3d to = tp.add(0, 1.2, 0);

            BlockHitResult hit = sw.raycast(new RaycastContext(
                    from, to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mob
            ));

            if (hit.getType() == HitResult.Type.MISS) return y; // 射線が通る高さ
        }

        // どこでも通らないなら高めに逃げる
        return base + 8.0;
    }

    private double estimateGroundDistance(ServerWorld sw, Vec3d from) {
        Vec3d to = from.add(0, -32, 0);
        var hit = sw.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mob
        ));
        if (hit.getType() == HitResult.Type.MISS) return 32.0;
        return from.y - hit.getPos().y;
    }

    private void startHyperCannon() {
        hyperSetTicks = 12;
        hyperBeamTicks = 0;
        beamsSpawned = false;

        cdHyper = 20 * 8;
        alice.requestBrAction(StudentBrAction.HYPER_CANNON_SET, hyperSetTicks);
    }

    private void tickHyperCannon(ServerWorld sw) {
        if (hyperSetTicks > 0) {
            alice.requestBrAction(StudentBrAction.HYPER_CANNON_SET, hyperSetTicks);
            hyperSetTicks--;

            if (hyperSetTicks == 0) {
                hyperBeamTicks = 20;
                beamsSpawned = false; // 念のため
            }
            return;
        }

        if (hyperBeamTicks > 0) {
            alice.requestBrAction(StudentBrAction.HYPER_CANNON, hyperBeamTicks);

            // ★照射開始の最初のtickでだけ spawn
            if (!beamsSpawned) {
                spawnBeamsOnce(sw);
                beamsSpawned = true;
            }

            hyperBeamTicks--;
        }
    }

    private void spawnBeamsOnce(ServerWorld sw) {
        if (target == null || !target.isAlive()) return;

        HyperCannonEntity left = new HyperCannonEntity(sw);
        left.init(alice, target, HyperCannonEntity.Side.LEFT);
        sw.spawnEntity(left);

        HyperCannonEntity right = new HyperCannonEntity(sw);
        right.init(alice, target, HyperCannonEntity.Side.RIGHT);
        sw.spawnEntity(right);
    }

    private LivingEntity findTarget() {
        double r = 24.0;
        var box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e instanceof HostileEntity
        ).stream().min(java.util.Comparator.comparingDouble(mob::squaredDistanceTo)).orElse(null);
    }

    private static double clamp(double v, double mn, double mx) {
        return Math.max(mn, Math.min(mx, v));
    }
}