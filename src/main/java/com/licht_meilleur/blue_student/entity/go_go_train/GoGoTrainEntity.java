package com.licht_meilleur.blue_student.entity.go_go_train;

import com.licht_meilleur.blue_student.entity.HikariEntity;
import com.licht_meilleur.blue_student.entity.NozomiEntity;
import com.licht_meilleur.blue_student.entity.projectile.GunTrainShellEntity;
import com.licht_meilleur.blue_student.registry.ModEntities;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class GoGoTrainEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===== link =====
    private UUID ownerPlayerUuid;
    private UUID targetUuid;
    private UUID nozomiPassengerUuid;
    private UUID hikariPassengerUuid;

    // ===== life =====
    private int lifeTicks = 0;
    private static final int MAX_LIFE = 20 * 15;

    // ===== phase =====
    private int phaseTicks = 0;
    private int phaseEndTick = 0;
    private boolean isCruisePhase = true;

    private static final int CRUISE_TICKS = 20 * 5;
    private static final int CHARGE_TICKS = 20 * 1;

    private static final double CRUISE_SPEED = 0.35;
    private static final double CHARGE_SPEED = 1.25;

    // ===== cruise (free) =====
    private float theta = 0f;
    private float radius = 9.0f; // ★半径大きめ（好みで 8～12）
    private float omega = 0.12f;
    private boolean clockwise = true;

    // ===== charge hit =====
    private static final float CHARGE_HIT_RADIUS = 1.6f;
    private static final float CHARGE_DAMAGE = 8.0f;
    private static final double CHARGE_KB_H = 1.8;
    private static final double CHARGE_KB_Y = 0.55;
    private static final int CHARGE_MIN_AGE = 6;

    // ===== gun =====
    private int fireCooldown = 0;
    private static final int FIRE_CD = 12;

    // ===== seats =====
    // ノゾミの後方にヒカリを配置
    private static final double HK_BACK = 2.8;   // ★少し広げた（好みで）
    private static final double HK_RIGHT = 0.0;
    private static final double HK_UP = 0.0;

    // ===== sync =====
    private static final TrackedData<Float> SYNC_BODY_YAW =
            DataTracker.registerData(GoGoTrainEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> SYNC_SHEET2_YAW =
            DataTracker.registerData(GoGoTrainEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Integer> SYNC_TARGET_EID =
            DataTracker.registerData(GoGoTrainEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public GoGoTrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SYNC_BODY_YAW, 0.0f);
        this.dataTracker.startTracking(SYNC_SHEET2_YAW, 0.0f);
        this.dataTracker.startTracking(SYNC_TARGET_EID, -1);
    }

    // ===== setters =====
    public GoGoTrainEntity setOwnerPlayerUuid(UUID id) { this.ownerPlayerUuid = id; return this; }
    public GoGoTrainEntity setTargetUuid(UUID id) { this.targetUuid = id; return this; }
    public GoGoTrainEntity setNozomiPassengerUuid(UUID id) { this.nozomiPassengerUuid = id; return this; }
    public GoGoTrainEntity setHikariPassengerUuid(UUID id) { this.hikariPassengerUuid = id; return this; }
    public GoGoTrainEntity setClockwise(boolean clockwise) { this.clockwise = clockwise; return this; }

    public UUID getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public UUID getTargetUuid() { return this.targetUuid; }

    // ===== model read =====
    public float getBodyYawDegSynced() { return this.dataTracker.get(SYNC_BODY_YAW); }
    public float getSheet2YawDeg()     { return this.dataTracker.get(SYNC_SHEET2_YAW); }
    public int getSyncedTargetEntityId() { return this.dataTracker.get(SYNC_TARGET_EID); }

    private void setBodyYawSynced(float yawDeg) {
        float y = MathHelper.wrapDegrees(yawDeg);
        this.prevYaw = y;
        this.setYaw(y);
        this.setPitch(0.0f);
        this.dataTracker.set(SYNC_BODY_YAW, y);
    }

    private void setSheet2YawServer(float yawDeg) {
        this.dataTracker.set(SYNC_SHEET2_YAW, MathHelper.wrapDegrees(yawDeg));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        if (++lifeTicks > MAX_LIFE) { discardAndRelease(); return; }
        if (!isOwnerAlive(sw))      { discardAndRelease(); return; }

        ensurePassengersMounted(sw);

        Entity target = (targetUuid != null) ? sw.getEntity(targetUuid) : null;
        if (target == null || !target.isAlive()) {
            this.setVelocity(Vec3d.ZERO);
            this.dataTracker.set(SYNC_TARGET_EID, -1);
            return;
        }

        this.dataTracker.set(SYNC_TARGET_EID, target.getId());

        // ===== turret aim (always) =====
        float targetYaw = computeYawToTarget(target);
        setSheet2YawServer(targetYaw);

        // ===== phase velocity decide =====
        tickPhase(sw, target);

        // ===== fire (cruise only) =====
        if (isCruisePhase) {
            if (fireCooldown > 0) fireCooldown--;
            if (fireCooldown == 0) {
                fireCooldown = FIRE_CD;
                if (target instanceof LivingEntity le) fireTwinCannonsShell(sw, le);
            }
        }

        // ===== move once =====
        this.move(MovementType.SELF, this.getVelocity());

        // ===== face velocity =====
        faceVelocityAndSyncYaw();

        // ===== hikari lock (pos = behind nozomi, yaw = targetYaw) =====
        lockHikariBehindNozomi(sw, targetYaw);
    }

    private float computeYawToTarget(Entity target) {
        Vec3d d = target.getEyePos().subtract(this.getPos());
        if (d.horizontalLengthSquared() > 1.0e-6) {
            return (float)(MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
        }
        return this.getYaw();
    }

    // ===== phases =====
    private void tickPhase(ServerWorld sw, Entity target) {
        if (phaseEndTick <= 0) {
            isCruisePhase = true;
            phaseTicks = 0;
            phaseEndTick = CRUISE_TICKS;
        }

        if (phaseTicks >= phaseEndTick) {
            isCruisePhase = !isCruisePhase;
            phaseTicks = 0;
            phaseEndTick = isCruisePhase ? CRUISE_TICKS : CHARGE_TICKS;
        }

        if (isCruisePhase) tickCruiseFree(sw, target);
        else tickCharge(sw, target);

        phaseTicks++;
    }

    // ===== cruise =====
    private void tickCruiseFree(ServerWorld sw, Entity target) {
        theta += (clockwise ? +1 : -1) * Math.abs(omega);

        Vec3d goal = pickCruiseGoal(sw, target);
        Vec3d dir = goal.subtract(this.getPos());

        Vec3d v = (dir.lengthSquared() > 1e-6)
                ? dir.normalize().multiply(CRUISE_SPEED)
                : Vec3d.ZERO;

        this.setVelocity(v);
    }

    private Vec3d pickCruiseGoal(ServerWorld sw, Entity target) {
        Vec3d center = target.getPos();

        float base = theta;
        float step = 0.70f; // ★少し強め（通り道探し）
        double r = radius;
        double gy = center.y + 0.2;

        for (int i = 0; i < 6; i++) {
            float a = base + (clockwise ? +1 : -1) * (i * step);
            double gx = center.x + Math.cos(a) * r;
            double gz = center.z + Math.sin(a) * r;
            Vec3d goal = new Vec3d(gx, gy, gz);

            if (isLineClear(sw, this.getPos(), goal)) {
                theta = a;
                return goal;
            }
        }

        return new Vec3d(center.x, gy, center.z);
    }

    private boolean isLineClear(ServerWorld sw, Vec3d from, Vec3d to) {
        HitResult hit = sw.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ===== charge =====
    private void tickCharge(ServerWorld sw, Entity target) {
        Vec3d from = this.getPos();
        Vec3d to = target.getPos().add(0, 0.2, 0);
        Vec3d d = to.subtract(from);

        if (d.lengthSquared() > 1e-6) this.setVelocity(d.normalize().multiply(CHARGE_SPEED));
        else this.setVelocity(Vec3d.ZERO);

        if (lifeTicks > CHARGE_MIN_AGE) tryChargeHit(sw);
    }

    private void tryChargeHit(ServerWorld sw) {
        Vec3d c = this.getPos();
        Box box = new Box(c, c).expand(CHARGE_HIT_RADIUS, 1.6, CHARGE_HIT_RADIUS);

        var list = sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive);
        if (list.isEmpty()) return;

        HostileEntity best = null;
        double bestD2 = 1e18;
        for (var h : list) {
            double d2 = h.squaredDistanceTo(c);
            if (d2 < bestD2) { bestD2 = d2; best = h; }
        }
        if (best == null) return;

        DamageSource src = sw.getDamageSources().generic();
        best.damage(src, CHARGE_DAMAGE);

        applyStrongKnockback(best, c);
    }

    private void applyStrongKnockback(LivingEntity victim, Vec3d fromPos) {
        Vec3d push = victim.getPos().subtract(fromPos);
        if (push.horizontalLengthSquared() < 1e-6) push = new Vec3d(0, 0, 1);

        Vec3d dir = new Vec3d(push.x, 0, push.z).normalize();
        Vec3d kb = dir.multiply(CHARGE_KB_H).add(0, CHARGE_KB_Y, 0);

        victim.setVelocity(kb);
        victim.velocityModified = true;
    }

    // ===== yaw from velocity =====
    private void faceVelocityAndSyncYaw() {
        Vec3d v = this.getVelocity();
        if (v.horizontalLengthSquared() < 1.0e-6) {
            this.dataTracker.set(SYNC_BODY_YAW, MathHelper.wrapDegrees(this.getYaw()));
            return;
        }
        float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
        setBodyYawSynced(yaw);
    }

    // ===== gun fire =====
    private void fireTwinCannonsShell(ServerWorld sw, LivingEntity target) {
        Vec3d startL = getMuzzlePos(WeaponSpec.MuzzleLocator.LEFT_SUB_MUZZLE);
        Vec3d startR = getMuzzlePos(WeaponSpec.MuzzleLocator.RIGHT_SUB_MUZZLE);

        Vec3d dirL = target.getEyePos().subtract(startL).normalize();
        Vec3d dirR = target.getEyePos().subtract(startR).normalize();

        spawnShell(sw, target, startL, dirL, -1);
        spawnShell(sw, target, startR, dirR, +1);
    }

    private void spawnShell(ServerWorld sw, LivingEntity target, Vec3d start, Vec3d dir, int curveSign) {
        GunTrainShellEntity shell = new GunTrainShellEntity(ModEntities.GUN_TRAIN_SHELL, sw)
                .setOwnerUuid(ownerPlayerUuid)
                .setTarget(target)
                .setCurveSign(curveSign);

        shell.setPosition(start.x, start.y, start.z);
        shell.setVelocity(dir.normalize().multiply(1.25));
        sw.spawnEntity(shell);
    }

    private Vec3d getMuzzlePos(WeaponSpec.MuzzleLocator loc) {
        Vec3d forward = forwardFromYaw(this.getYaw()).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d base = this.getPos().add(0, 0.75, 0);

        return switch (loc) {
            case LEFT_SUB_MUZZLE  -> base.add(right.multiply(-0.75)).add(forward.multiply(0.65));
            case RIGHT_SUB_MUZZLE -> base.add(right.multiply(+0.75)).add(forward.multiply(0.65));
            default -> base.add(forward.multiply(0.65));
        };
    }

    private static Vec3d forwardFromYaw(float yawDeg) {
        float r = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(r), 0, MathHelper.cos(r));
    }

    // ===== passengers =====
    private void ensurePassengersMounted(ServerWorld sw) {
        // ノゾミだけ騎乗（ヒカリは固定配置で制御）
        if (nozomiPassengerUuid != null) {
            Entity n = sw.getEntity(nozomiPassengerUuid);
            if (n instanceof NozomiEntity noz && noz.isAlive()) {
                if (noz.getVehicle() != this) {
                    noz.stopRiding();
                    noz.startRiding(this, true);
                }
            }
        }
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        float bodyYaw = this.getYaw();

        if (passenger instanceof NozomiEntity) {
            Vec3d seat = getSeatWorldNozomi();
            updater.accept(passenger, seat.x, seat.y, seat.z);
            setPassengerYawOnly(passenger, bodyYaw);
            return;
        }

        Vec3d seat = this.getPos().add(0, 0.9, 0);
        updater.accept(passenger, seat.x, seat.y, seat.z);
        setPassengerYawOnly(passenger, bodyYaw);
    }

    private void setPassengerYawOnly(Entity passenger, float yaw) {
        passenger.prevYaw = yaw;
        passenger.setYaw(yaw);

        if (passenger instanceof LivingEntity le) {
            le.bodyYaw = yaw;
            le.headYaw = yaw;
            le.prevBodyYaw = yaw;
            le.prevHeadYaw = yaw;
        }
    }

    private Vec3d getSeatWorldNozomi() {
        Vec3d forward = forwardFromYaw(this.getYaw()).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        return this.getPos()
                .add(0, 0.90, 0)
                .add(forward.multiply(-0.10))
                .add(right.multiply(0.00));
    }

    private void lockHikariBehindNozomi(ServerWorld sw, float lookYaw) {
        if (hikariPassengerUuid == null || nozomiPassengerUuid == null) return;

        Entity h = sw.getEntity(hikariPassengerUuid);
        Entity n = sw.getEntity(nozomiPassengerUuid);

        if (!(h instanceof HikariEntity hk) || !hk.isAlive()) return;
        if (!(n instanceof NozomiEntity noz) || !noz.isAlive()) return;

        if (hk.hasVehicle()) hk.stopRiding();

        float baseYaw = noz.getYaw();
        Vec3d forward = forwardFromYaw(baseYaw).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        Vec3d base = noz.getPos();
        Vec3d pos = base
                .add(0, HK_UP, 0)
                .add(forward.multiply(-HK_BACK))
                .add(right.multiply(HK_RIGHT));

        hk.setPos(pos.x, pos.y, pos.z);
        hk.prevX = pos.x; hk.prevY = pos.y; hk.prevZ = pos.z;

        float y = MathHelper.wrapDegrees(lookYaw);

        hk.prevYaw = y;
        hk.setYaw(y);
        hk.setPitch(0f);

        hk.setVelocity(Vec3d.ZERO);
        hk.velocityModified = true;
        hk.setNoGravity(true);
        hk.noClip = true;

        hk.bodyYaw = y;
        hk.headYaw = y;
        hk.prevBodyYaw = y;
        hk.prevHeadYaw = y;
    }

    // ===== owner / discard =====
    private boolean isOwnerAlive(ServerWorld sw) {
        if (ownerPlayerUuid == null) return false;

        for (NozomiEntity n : sw.getEntitiesByClass(
                NozomiEntity.class,
                this.getBoundingBox().expand(128),
                e -> e.isAlive()
        )) {
            if (ownerPlayerUuid.equals(n.getOwnerUuid())) return true;
        }
        return false;
    }

    private void discardAndRelease() {
        for (Entity p : this.getPassengerList()) p.stopRiding();
        this.discard();
    }

    // ===== NBT =====
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("OwnerP")) ownerPlayerUuid = nbt.getUuid("OwnerP");
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");
        if (nbt.containsUuid("NozomiP")) nozomiPassengerUuid = nbt.getUuid("NozomiP");
        if (nbt.containsUuid("HikariP")) hikariPassengerUuid = nbt.getUuid("HikariP");

        lifeTicks = nbt.getInt("Life");

        phaseTicks = nbt.getInt("PhaseT");
        phaseEndTick = nbt.getInt("PhaseE");
        isCruisePhase = nbt.getBoolean("Cruise");

        theta = nbt.getFloat("Theta");
        radius = nbt.getFloat("Radius");
        omega = nbt.getFloat("Omega");
        clockwise = nbt.getBoolean("Clockwise");

        fireCooldown = nbt.getInt("FireCd");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerPlayerUuid != null) nbt.putUuid("OwnerP", ownerPlayerUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        if (nozomiPassengerUuid != null) nbt.putUuid("NozomiP", nozomiPassengerUuid);
        if (hikariPassengerUuid != null) nbt.putUuid("HikariP", hikariPassengerUuid);

        nbt.putInt("Life", lifeTicks);

        nbt.putInt("PhaseT", phaseTicks);
        nbt.putInt("PhaseE", phaseEndTick);
        nbt.putBoolean("Cruise", isCruisePhase);

        nbt.putFloat("Theta", theta);
        nbt.putFloat("Radius", radius);
        nbt.putFloat("Omega", omega);
        nbt.putBoolean("Clockwise", clockwise);

        nbt.putInt("FireCd", fireCooldown);
    }

    // ===== GeckoLib =====
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            boolean moving = this.getVelocity().horizontalLengthSquared() > 0.0008;
            state.setAndContinue(moving
                    ? RawAnimation.begin().thenLoop("animation.go")
                    : RawAnimation.begin().thenLoop("animation.stop"));
            return PlayState.CONTINUE;
        }));
    }
}