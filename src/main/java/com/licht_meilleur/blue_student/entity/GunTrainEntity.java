package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.network.ServerFx;
import com.licht_meilleur.blue_student.weapon.ProjectileWeaponAction;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class GunTrainEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ★中身は「プレイヤーUUID」（合体スキル共有キー）
    private UUID ownerPlayerUuid;

    // ★自分が牽引されている車体
    private UUID trainUuid;

    // ★自分（GunTrain）に乗る生徒（=Hikari）
    private UUID passengerStudentUuid;

    private int fireCooldown = 0;
    private int lifeTicks = 0;
    private static final int MAX_LIFE = 20 * 15;

    public GunTrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public GunTrainEntity setOwnerPlayerUuid(UUID ownerPlayerUuid) {
        this.ownerPlayerUuid = ownerPlayerUuid;
        return this;
    }

    public GunTrainEntity setTrainUuid(UUID trainUuid) {
        this.trainUuid = trainUuid;
        return this;
    }

    public GunTrainEntity setPassengerStudentUuid(UUID passengerStudentUuid) {
        this.passengerStudentUuid = passengerStudentUuid;
        return this;
    }

    public UUID getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public UUID getTrainUuid() { return trainUuid; }
    public UUID getPassengerStudentUuid() { return passengerStudentUuid; }

    @Override
    protected void initDataTracker() { }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        tickAnchor();


        if (++lifeTicks > MAX_LIFE) {
            this.discard();
            return;
        }
        if (!isOwnerAlive(sw)) {
            this.discard();
            return;
        }


        // ===== 単体時は敵方向を向く =====
        if (!this.hasVehicle()) {
            LivingEntity target = findTarget(sw);
            if (target != null) {
                Vec3d dir = target.getPos().subtract(this.getPos());
                if (dir.horizontalLengthSquared() > 1e-6) {
                    float yaw = (float)(MathHelper.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90f;
                    this.setYaw(yaw);
                    this.setBodyYaw(yaw);
                    this.setHeadYaw(yaw);
                }
            }
        }



        // ★車体がいないなら消える（孤児対策）
        if (trainUuid != null) {
            Entity t = sw.getEntity(trainUuid);
            if (t == null || !t.isAlive()) {
                this.discard();
                return;
            }
        }
        // ★向き制御（単体：敵方向 / 牽引中：trainYaw-90）
        LivingEntity target = findTarget(sw);
        if (target != null) {
            Vec3d from = this.getPos();
            Vec3d to = target.getEyePos();
            Vec3d d = to.subtract(from);
            if (d.horizontalLengthSquared() > 1.0e-6) {
                float yaw = (float)(MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
                this.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), yaw, 0.0f);
            }
        }

        // ★Hikari を GunTrain に乗せる（NozomiはTrain側）
        ensurePassengerMounted(sw);

        if (fireCooldown > 0) fireCooldown--;
        if (fireCooldown == 0) {
            fireCooldown = 12; // 好み：連射間隔
            fireTwinCannons(sw); // ★ここを呼ぶ（2門射撃）
        }
    }

    private void ensurePassengerMounted(ServerWorld sw) {
        if (passengerStudentUuid == null) return;
        Entity p = sw.getEntity(passengerStudentUuid);
        if (!(p instanceof AbstractStudentEntity st) || !st.isAlive()) return;

        if (st.getVehicle() != this) {
            st.stopRiding();
            st.startRiding(this, true);
        }
    }

    // ===== 2門射撃 =====
    private void fireTwinCannons(ServerWorld sw) {
        LivingEntity target = findTarget(sw);
        if (target == null) return;

        WeaponSpec specL = WeaponSpecs.forGunTrainLeft();   // muzzleLocator = LEFT_SUB_MUZZLE
        WeaponSpec specR = WeaponSpecs.forGunTrainRight();  // muzzleLocator = RIGHT_SUB_MUZZLE

        Vec3d startL = getMuzzlePos(specL.muzzleLocator);
        Vec3d dirL = target.getEyePos().subtract(startL).normalize();

        Vec3d startR = getMuzzlePos(specR.muzzleLocator);
        Vec3d dirR = target.getEyePos().subtract(startR).normalize();

        ProjectileWeaponAction act = new ProjectileWeaponAction();

        act.shootFromCustomPos(this, target, specL, startL, dirL);
        ServerFx.sendShotFx(sw, this.getId(), startL, specL.fxType, specL.fxWidth, new Vec3d[]{dirL}, (float) specL.range);

        act.shootFromCustomPos(this, target, specR, startR, dirR);
        ServerFx.sendShotFx(sw, this.getId(), startR, specR.fxType, specR.fxWidth, new Vec3d[]{dirR}, (float) specR.range);
    }

    private LivingEntity findTarget(ServerWorld sw) {
        double r = 18.0;
        Box box = this.getBoundingBox().expand(r, 6.0, r);
        var list = sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive);
        if (list.isEmpty()) return null;

        HostileEntity best = null;
        double bestD2 = 1e18;
        for (HostileEntity h : list) {
            double d2 = this.squaredDistanceTo(h);
            if (d2 < bestD2) { bestD2 = d2; best = h; }
        }
        return best;
    }

    private Vec3d getMuzzlePos(WeaponSpec.MuzzleLocator loc) {
        // ★まずは「エンティティ基準の近似」
        Vec3d forward = Vec3d.fromPolar(0, this.getYaw()).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d base = this.getPos().add(0, 0.75, 0);

        return switch (loc) {
            case LEFT_SUB_MUZZLE  -> base.add(right.multiply(-0.35)).add(forward.multiply(0.65));
            case RIGHT_SUB_MUZZLE -> base.add(right.multiply(+0.35)).add(forward.multiply(0.65));
            default -> base.add(forward.multiply(0.65));
        };
    }

    // ===== passenger seat positioning（GunTrainに乗ったHikari用）=====
    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater updater) {
        super.updatePassengerPosition(passenger, updater);

        Vec3d seat = getSeatWorldGun();
        updater.accept(passenger, seat.x, seat.y, seat.z);

        float y = this.getYaw(); // 必要なら +90/-90

        passenger.setYaw(y);
        passenger.setBodyYaw(y);
        passenger.setVelocity(Vec3d.ZERO);

        if (passenger instanceof LivingEntity le) {
            le.setHeadYaw(y);
            le.setPitch(0);
        }

    }

    // ===== NBT =====
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("OwnerP")) ownerPlayerUuid = nbt.getUuid("OwnerP");
        if (nbt.containsUuid("Train")) trainUuid = nbt.getUuid("Train");
        if (nbt.containsUuid("Passenger")) passengerStudentUuid = nbt.getUuid("Passenger");
        fireCooldown = nbt.getInt("FireCd");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerPlayerUuid != null) nbt.putUuid("OwnerP", ownerPlayerUuid);
        if (trainUuid != null) nbt.putUuid("Train", trainUuid);
        if (passengerStudentUuid != null) nbt.putUuid("Passenger", passengerStudentUuid);
        nbt.putInt("FireCd", fireCooldown);
    }

    // ===== GeckoLib =====
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            state.setAndContinue(RawAnimation.begin().thenLoop("animation.go"));
            return PlayState.CONTINUE;
        }));
    }
    private static Vec3d locatorPxToWorld(Vec3d locPx, float yawDeg, Vec3d basePos) {
        double yawRad = Math.toRadians(yawDeg);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double lx = locPx.x / 16.0;
        double ly = locPx.y / 16.0;
        double lz = locPx.z / 16.0;

        double rx = lx * cos - lz * sin;
        double rz = lx * sin + lz * cos;

        return basePos.add(rx, ly, rz);
    }
    private void faceTarget(LivingEntity target) {
        Vec3d from = this.getPos();
        Vec3d to = target.getEyePos();
        Vec3d d = to.subtract(from);

        if (d.horizontalLengthSquared() < 1.0e-6) return;

        float yaw = (float)(MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
        this.setYaw(yaw);
        this.setPitch(0);
        this.setRotation(yaw, 0);
        this.velocityModified = true;
    }

    private static final Vec3d SEAT_PX   = new Vec3d(0.4, 13.4, 6.6);
    private static final Vec3d SEAT_TUNE = new Vec3d(0.0, 0.0, -12.0);

    private Vec3d getSeatWorldGun() {
        return locatorPxToWorld(SEAT_PX.add(SEAT_TUNE), this.getYaw(), this.getPos());
    }
    private Vec3d anchorPos = null;

    public void setAnchorPos(Vec3d p) {
        this.anchorPos = p;
    }

    private void tickAnchor() {
        if (anchorPos == null) return;
        // ★車両(牽引)中はアンカーしない
        if (this.hasVehicle()) return;

        this.refreshPositionAndAngles(anchorPos.x, anchorPos.y, anchorPos.z, this.getYaw(), 0);
        this.setVelocity(Vec3d.ZERO);
        this.velocityModified = true;
    }
    private boolean isOwnerAlive(ServerWorld sw) {
        if (ownerPlayerUuid == null) return false;

        for (HikariEntity h : sw.getEntitiesByClass(
                HikariEntity.class,
                this.getBoundingBox().expand(128),
                e -> e.isAlive()
        )) {
            if (ownerPlayerUuid.equals(h.getOwnerUuid())) return true;
        }
        return false;
    }
}