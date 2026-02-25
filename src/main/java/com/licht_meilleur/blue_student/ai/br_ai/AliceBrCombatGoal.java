package com.licht_meilleur.blue_student.ai.br_ai;

import com.licht_meilleur.blue_student.entity.AliceEntity;
import com.licht_meilleur.blue_student.entity.projectile.SonicBeamEntity;
import com.licht_meilleur.blue_student.registry.ModEntities;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.EnumSet;

public class AliceBrCombatGoal extends Goal {
    private final PathAwareEntity mob;
    private final AliceEntity alice;

    private LivingEntity target;

    // ---- orbit ----
    private int orbitDir = 1; // +1 / -1
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

    // ---- hyper ----
    private int hyperSetTicks = 0;
    private int hyperBeamTicks = 0;

    public AliceBrCombatGoal(PathAwareEntity mob, AliceEntity alice) {
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
    }

    @Override
    public void stop() {
        target = null;
        mob.setTarget(null);
        mob.getNavigation().stop();

        hyperSetTicks = 0;
        hyperBeamTicks = 0;
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return;

        if (cdMain > 0) cdMain--;
        if (cdHyper > 0) cdHyper--;

        if (target == null || !target.isAlive()) {
            target = findTarget();
            mob.setTarget(target);
            return;
        }

        boolean canSee = mob.getVisibilityCache().canSee(target);

        // 見た目：常にターゲット（アリス本体の向き）
        alice.requestLookTarget(target, 80, 2);
        mob.getLookControl().lookAt(target, 90.0f, 90.0f);

        // ==============================
        // ハイパー中：その場に静止して照射処理
        // ==============================
        if (hyperSetTicks > 0 || hyperBeamTicks > 0) {
            freezeInPlaceForHyper();
            tickHyperCannon(sw);
            return;
        }

        // ==============================
        // 通常：旋回＋高度制御
        // ==============================
        tickOrbitRandom();
        tickOrbitMoveAndAltitude(sw, canSee);

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

    // ----------------------------
    // Hyper: set → beam (継続)
    // ----------------------------
    private void startHyperCannon() {
        hyperSetTicks = 12;
        hyperBeamTicks = 0;

        cdHyper = 20 * 8;
        alice.requestBrAction(StudentBrAction.HYPER_CANNON_SET, hyperSetTicks);
    }

    private void tickHyperCannon(ServerWorld sw) {

        // セット中
        if (hyperSetTicks > 0) {
            alice.requestBrAction(StudentBrAction.HYPER_CANNON_SET, hyperSetTicks);
            hyperSetTicks--;

            if (hyperSetTicks == 0) {
                hyperBeamTicks = 20; // 照射時間
            }
            return;
        }

        // 照射中
        if (hyperBeamTicks > 0) {
            alice.requestBrAction(StudentBrAction.HYPER_CANNON, hyperBeamTicks);

            // ★ここが重要：毎tick描画＆判定
            spawnBeamsEveryTick(sw);

            hyperBeamTicks--;
        }
    }

    private void freezeInPlaceForHyper() {
        // 移動停止＆慣性停止
        mob.getNavigation().stop();
        mob.setVelocity(Vec3d.ZERO);
        mob.velocityDirty = true;

        // 空中静止用（落下させたくない）
        mob.setNoGravity(true);
        mob.fallDistance = 0;
        mob.setOnGround(false);

        // アニメ的には「HYPER_CANNON_SET / HYPER_CANNON」側で制御
    }

    private void spawnBeamsContinuous(ServerWorld sw) {
        if (target == null || !target.isAlive()) return;

        spawnOneBeam(sw, -1); // LEFT
        spawnOneBeam(sw,  1); // RIGHT
    }

    private void spawnOneBeam(ServerWorld sw, int sideSign) {

        Vec3d start = muzzleSideTowardTarget(sideSign);
        Vec3d end = target.getEyePos();

        // 壁で止める
        BlockHitResult hit = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mob
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getPos();
        }

        // ---- ★ パーティクル描画 ----
        spawnBeamParticles(sw, start, end);

        // ---- ★ ダメージ ----
        damageAlongSegment(sw, start, end);
    }
    private void spawnBeamsEveryTick(ServerWorld sw) {
        if (target == null || !target.isAlive()) return;

        spawnOneBeam(sw, -1); // LEFT
        spawnOneBeam(sw,  1); // RIGHT
    }


    private void spawnBeamParticles(ServerWorld sw, Vec3d start, Vec3d end) {

        Vec3d dir = end.subtract(start);
        double length = dir.length();
        if (length < 0.001) return;

        Vec3d forward = dir.normalize();
        Vec3d step = forward.multiply(0.35);

        Vec3d pos = start;

        for (double traveled = 0; traveled < length; traveled += 0.35) {

            for (int i = 0; i < 3; i++) {

                double ox = (sw.random.nextDouble() - 0.5) * 0.25;
                double oy = (sw.random.nextDouble() - 0.5) * 0.25;
                double oz = (sw.random.nextDouble() - 0.5) * 0.25;

                sw.spawnParticles(
                        net.minecraft.particle.ParticleTypes.END_ROD,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        1,
                        0,0,0,
                        0
                );

                sw.spawnParticles(
                        net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        1,
                        0,0,0,
                        0
                );
            }

            // 中央にソニック衝撃
            sw.spawnParticles(
                    net.minecraft.particle.ParticleTypes.SONIC_BOOM,
                    pos.x, pos.y, pos.z,
                    1,
                    0,0,0,
                    0
            );

            pos = pos.add(step);
        }
    }

    private void damageAlongSegment(ServerWorld sw, Vec3d start, Vec3d end) {
        double radius = 0.8;
        Box box = new Box(start, end).expand(radius);

        var entities = sw.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != alice
        );

        for (LivingEntity e : entities) {
            double distSq = distanceSqPointToSegment(
                    e.getPos().add(0, e.getHeight() * 0.5, 0),
                    start, end
            );
            if (distSq <= radius * radius) {
                // ここは好みで：magic / sonicboom風 / 貫通 etc
                e.damage(sw.getDamageSources().magic(), 6.0f);
            }
        }
    }

    // ----------------------------
    // Orbit movement
    // ----------------------------
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

        // “射線が通る高度”を探す（軽い簡易版）
        for (int i = 0; i <= 10; i++) {
            double y = base + i * 0.8;
            Vec3d from = new Vec3d(my.x, y, my.z);
            Vec3d to = tp.add(0, 1.2, 0);

            BlockHitResult hit = sw.raycast(new RaycastContext(
                    from, to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mob
            ));
            if (hit.getType() == HitResult.Type.MISS) return y;
        }

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

    // ----------------------------
    // Utils
    // ----------------------------
    private LivingEntity findTarget() {
        double r = 24.0;
        var box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e instanceof HostileEntity
        ).stream().min(java.util.Comparator.comparingDouble(mob::squaredDistanceTo)).orElse(null);
    }

    private Vec3d muzzleSideTowardTarget(int sign) {
        // sign: -1 = left, +1 = right
        Vec3d base = alice.getEyePos().subtract(0, 0.10, 0);

        Vec3d toT = target.getEyePos().subtract(base);
        Vec3d forward = toT.lengthSquared() < 1e-6 ? alice.getRotationVec(1.0f) : toT.normalize();

        // right = up x forward
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(forward);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        double side = 0.25 * sign; // 幅
        double fwd  = 0.15;        // 少し前へ
        return base.add(right.multiply(side)).add(forward.multiply(fwd));
    }

    private static double distanceSqPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < 1e-6) return p.squaredDistanceTo(a);

        double t = p.subtract(a).dotProduct(ab) / abLenSq;
        t = Math.max(0, Math.min(1, t));
        Vec3d proj = a.add(ab.multiply(t));
        return p.squaredDistanceTo(proj);
    }

    private static double clamp(double v, double mn, double mx) {
        return Math.max(mn, Math.min(mx, v));
    }
}