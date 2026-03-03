package com.licht_meilleur.blue_student.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
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

public class TrainEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 合体スキル共有キー（プレイヤーUUID）
    private UUID ownerPlayerUuid;

    // passenger として連結される GunTrain
    private UUID gunTrainUuid;

    // 周回中心（敵）
    private UUID targetUuid;

    private float theta = 0f;
    private float radius = 6.0f;
    private float omega = 0.12f;
    private int stuckTicks = 0;

    private int lifeTicks = 0;
    private static final int MAX_LIFE = 20 * 15; // 15秒

    private boolean clockwise = true;
    public TrainEntity setClockwise(boolean clockwise) { this.clockwise = clockwise; return this; }

    public TrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() { }

    public TrainEntity setOwnerPlayerUuid(UUID ownerPlayerUuid) { this.ownerPlayerUuid = ownerPlayerUuid; return this; }
    public TrainEntity setGunTrainUuid(UUID gunUuid) { this.gunTrainUuid = gunUuid; return this; }
    public TrainEntity setTargetUuid(UUID target) { this.targetUuid = target; return this; }

    public UUID getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public UUID getGunTrainUuid() { return gunTrainUuid; }
    public UUID getTargetUuid() { return targetUuid; }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // ===== 寿命管理 =====
        if (++lifeTicks > MAX_LIFE) {
            this.discard();
            return;
        }

        // ===== owner不在なら消える =====
        if (!isOwnerAlive(sw)) {
            this.discard();
            return;
        }

        ensureGunTrainMounted(sw);

        Entity center = (targetUuid != null) ? sw.getEntity(targetUuid) : null;
        if (center == null || !center.isAlive()) {
            this.setVelocity(Vec3d.ZERO);
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }

        float step = Math.abs(omega);
        theta += clockwise ? -step : +step;

        double cx = center.getX();
        double cz = center.getZ();

        double gx = cx + Math.cos(theta) * radius;
        double gz = cz + Math.sin(theta) * radius;
        double gy = center.getY();

        Vec3d goal = new Vec3d(gx, gy, gz);
        Vec3d dir = goal.subtract(this.getPos());

        if (dir.lengthSquared() > 1e-6) {
            this.setVelocity(dir.normalize().multiply(0.35));
        } else {
            this.setVelocity(Vec3d.ZERO);
        }

        this.move(MovementType.SELF, this.getVelocity());

        // ===== 進行方向を向く =====
        // move() のあと
        Vec3d v = this.getVelocity();
        if (v.horizontalLengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;

            // ★ setYaw じゃなく refreshPositionAndAngles（prevYawも一緒に整う）
            this.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), yaw, 0.0f);
        }

    }

    // ===== passenger positioning =====
    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        super.updatePassengerPosition(passenger, updater);

        // ===== GunTrain牽引 =====
        if (passenger instanceof GunTrainEntity) {
            Vec3d tow = getTowPosForGunTrain();
            updater.accept(passenger, tow.x, tow.y, tow.z);

            float y = this.getYaw() - 90.0f;

            passenger.setYaw(y);
            passenger.setPitch(0.0f);

            if (passenger instanceof LivingEntity le) {
                le.setBodyYaw(y);
                le.setHeadYaw(y);
            }

            passenger.setVelocity(Vec3d.ZERO);
            passenger.velocityModified = true;
            return;
        }

        // ===== 生徒座席 =====
        Vec3d seat = getSeatWorldTrain();
        updater.accept(passenger, seat.x, seat.y, seat.z);

        float y = this.getYaw();

        passenger.setYaw(y);
        passenger.setPitch(0.0f);

        if (passenger instanceof LivingEntity le) {
            le.setBodyYaw(y);
            le.setHeadYaw(y);
        }

        passenger.setVelocity(Vec3d.ZERO);
        passenger.velocityModified = true;
    }

    private Vec3d getTowPosForGunTrain() {
        Vec3d forward = Vec3d.fromPolar(0, this.getYaw()).normalize();
        Vec3d back = forward.multiply(-1);

        // 後方に 1.8 ブロック（好みで）
        return this.getPos().add(back.multiply(1.8)).add(0, 0.0, 0);
    }


    // ===== NBT =====
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerPlayerUuid != null) nbt.putUuid("OwnerP", ownerPlayerUuid);
        if (gunTrainUuid != null) nbt.putUuid("Gun", gunTrainUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);

        nbt.putFloat("Theta", theta);
        nbt.putFloat("Radius", radius);
        nbt.putFloat("Omega", omega);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("OwnerP")) ownerPlayerUuid = nbt.getUuid("OwnerP");
        if (nbt.containsUuid("Gun")) gunTrainUuid = nbt.getUuid("Gun");
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");

        theta = nbt.getFloat("Theta");
        radius = nbt.getFloat("Radius");
        omega = nbt.getFloat("Omega");
    }

    // ===== GeckoLib =====
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

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

    // ===== Nozzle smoke（Trainのみ）=====
    private static final Vec3d NOZZLE_LOC = new Vec3d(0.0, 45.0, -6.0); // geoのnozzle（px）
    private static final double LOC_SCALE = 1.0 / 16.0;

    private Vec3d toWorldFromLocator(Vec3d locPx) {
        double yawRad = Math.toRadians(this.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double lx = locPx.x * LOC_SCALE;
        double ly = locPx.y * LOC_SCALE;
        double lz = locPx.z * LOC_SCALE;

        double rx = lx * cos - lz * sin;
        double rz = lx * sin + lz * cos;

        return this.getPos().add(rx, ly, rz);
    }

    private void spawnNozzleSmoke(ServerWorld sw) {
        // 軽め：2tickに1回、少量
        if ((this.age & 1) != 0) return;

        Vec3d p = toWorldFromLocator(NOZZLE_LOC);
        sw.spawnParticles(
                net.minecraft.particle.ParticleTypes.SMOKE,
                p.x, p.y, p.z,
                1,
                0.02, 0.02, 0.02,
                0.005
        );
    }
    // ===== locator(px) -> world =====
    private static Vec3d locatorPxToWorld(Vec3d locPx, float yawDeg, Vec3d basePos) {
        // Bedrock locator: x=右, y=上, z=前 を想定
        double yawRad = Math.toRadians(yawDeg);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double lx = locPx.x / 16.0;
        double ly = locPx.y / 16.0;
        double lz = locPx.z / 16.0;

        // yaw 回転（Y軸）
        double rx = lx * cos - lz * sin;
        double rz = lx * sin + lz * cos;

        return basePos.add(rx, ly, rz);
    }
    private void ensureGunTrainMounted(ServerWorld sw) {
        if (gunTrainUuid == null) return;

        Entity e = sw.getEntity(gunTrainUuid);
        if (!(e instanceof GunTrainEntity gun) || !gun.isAlive()) return;

        if (gun.getVehicle() != this) {
            gun.stopRiding();
            gun.startRiding(this, true);
        }
    }

    // ===== Train seat locator + tuning =====
    private static final Vec3d SEAT_PX   = new Vec3d(0.4, 13.4, 6.6);
    private static final Vec3d SEAT_TUNE = new Vec3d(0.0, 0.0, -12.0); // ★ここを調整

    private Vec3d getSeatWorldTrain() {
        // ★重要：基準は this.getPos()（エンティティ原点）
        return locatorPxToWorld(SEAT_PX.add(SEAT_TUNE), this.getYaw(), this.getPos());
    }
    private boolean isOwnerAlive(ServerWorld sw) {
        if (ownerPlayerUuid == null) return false;

        // ノゾミが存在するか
        for (NozomiEntity n : sw.getEntitiesByClass(
                NozomiEntity.class,
                this.getBoundingBox().expand(128),
                e -> e.isAlive()
        )) {
            if (ownerPlayerUuid.equals(n.getOwnerUuid())) return true;
        }
        return false;
    }
}