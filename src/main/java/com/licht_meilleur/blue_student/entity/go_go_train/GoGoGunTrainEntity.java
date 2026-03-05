package com.licht_meilleur.blue_student.entity.go_go_train;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.HikariEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import com.licht_meilleur.blue_student.entity.projectile.GunTrainShellEntity;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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

public class GoGoGunTrainEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID ownerPlayerUuid;
    private UUID trainUuid;
    private UUID passengerStudentUuid;

    private int fireCooldown = 0;
    private int lifeTicks = 0;
    private static final int MAX_LIFE = 20 * 15;

    private Vec3d anchorPos = null;

    private boolean mergedMode = false;
    public GoGoGunTrainEntity setMergedMode(boolean v){ this.mergedMode = v; return this; }
    public boolean isMergedMode(){ return mergedMode; }


    // ★追加：クライアントが砲塔回転用に参照できるターゲットEntityId
    private static final TrackedData<Integer> SYNC_TARGET_EID =
            DataTracker.registerData(GoGoGunTrainEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Float> SYNC_SHEET_YAW =
            DataTracker.registerData(GoGoGunTrainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SYNC_BODY_YAW =
            DataTracker.registerData(GoGoGunTrainEntity.class, TrackedDataHandlerRegistry.FLOAT);




    public GoGoGunTrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SYNC_BODY_YAW, 0.0f);
        this.dataTracker.startTracking(SYNC_SHEET_YAW, 0.0f);
        this.dataTracker.startTracking(SYNC_TARGET_EID, -1); // ★追加（これが無いと set した瞬間クラッシュ）
    }
    public float getBodyYawDegSynced() { return this.dataTracker.get(SYNC_BODY_YAW); }
    private void setBodyYawServer(float yaw) { this.dataTracker.set(SYNC_BODY_YAW, MathHelper.wrapDegrees(yaw)); }

    public float getSheetYawDeg() { return this.dataTracker.get(SYNC_SHEET_YAW); }
    private void setSheetYawServer(float yaw) { this.dataTracker.set(SYNC_SHEET_YAW, MathHelper.wrapDegrees(yaw)); }

    // ===== 外部セット =====
    public GoGoGunTrainEntity setOwnerPlayerUuid(UUID ownerPlayerUuid) { this.ownerPlayerUuid = ownerPlayerUuid; return this; }
    public GoGoGunTrainEntity setTrainUuid(UUID trainUuid) { this.trainUuid = trainUuid; return this; }
    public GoGoGunTrainEntity setPassengerStudentUuid(UUID passengerStudentUuid) { this.passengerStudentUuid = passengerStudentUuid; return this; }

    public UUID getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public UUID getTrainUuid() { return trainUuid; }
    public UUID getPassengerStudentUuid() { return passengerStudentUuid; }

    public void setAnchorPos(Vec3d p) { this.anchorPos = p; }

    // ★モデル側が見る（クライアントOK）
    public int getSyncedTargetEntityId() {
        return this.dataTracker.get(SYNC_TARGET_EID);
    }

    // サーバー側でyaw確定 & trackerへ
    private void setYawStableServer(float yaw) {
        float y = MathHelper.wrapDegrees(yaw);
        this.prevYaw = y;
        this.setYaw(y);
        this.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), y, 0.0f);

        // ★合体モードのbodyYawは tickFollowTrain が握る
        if (!mergedMode) {
            this.dataTracker.set(SYNC_BODY_YAW, y);
        }
    }

    @Override
    public void tick() {
        super.tick();


        if (this.getWorld().isClient) return;

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        tickFollowTrain(sw);

        if (++lifeTicks > MAX_LIFE) { discardAndNotify(sw); return; }
        if (!isOwnerAlive(sw))      { discardAndNotify(sw); return; }

        // 孤児対策
        if (trainUuid != null) {
            Entity t = sw.getEntity(trainUuid);
            if (t == null || !t.isAlive()) { discardAndNotify(sw); return; }
        }

        // ===== 向き制御 =====
        LivingEntity target = mergedMode ? findTrainTarget(sw) : findTarget(sw);

// ★ターゲット同期（クライアントが参照）
        this.dataTracker.set(SYNC_TARGET_EID, (target != null) ? target.getId() : -1);

// 1) 車体Yaw（合体時は先頭列車yaw固定なので回さない）
        if (!mergedMode && target != null) {
            Vec3d d = target.getEyePos().subtract(this.getPos());
            if (d.horizontalLengthSquared() > 1.0e-6) {
                float yaw = (float)(MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
                setYawStableServer(yaw);
            }
        }


// 2) sheetYaw（砲塔）
        if (target != null) {
            Vec3d d = target.getEyePos().subtract(this.getPos());
            if (d.horizontalLengthSquared() > 1.0e-6) {
                float aimYaw = (float)(MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
                setSheetYawServer(aimYaw);
            }
        } else {
            setSheetYawServer(this.getYaw());
        }


        ensurePassengerMounted(sw);

        // ===== 射撃可否 =====
        boolean allowFire = true;
        if (this.hasVehicle() && this.getVehicle() instanceof TrainEntity t) {
            allowFire = t.isGunFireEnabled();
        }

        if (allowFire) {
            if (fireCooldown > 0) fireCooldown--;
            if (fireCooldown == 0) {
                fireCooldown = 12;
                fireTwinCannonsShell(sw);
            }
        }
    }
    private LivingEntity findTrainTarget(ServerWorld sw) {
        if (trainUuid == null) return null;

        Entity t = sw.getEntity(trainUuid);
        if (!(t instanceof GoGoTrainEntity train) || !train.isAlive()) return null;

        UUID tu = train.getTargetUuid(); // ★ここ
        if (tu == null) return null;

        Entity e = sw.getEntity(tu);
        return (e instanceof LivingEntity le && le.isAlive()) ? le : null;
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

    private void fireTwinCannonsShell(ServerWorld sw) {
        LivingEntity target = findTarget(sw);
        if (target == null) {
            this.dataTracker.set(SYNC_TARGET_EID, -1);
            return;
        }

        // ★ここが超重要：クライアントにターゲットを伝える
        this.dataTracker.set(SYNC_TARGET_EID, target.getId());


        Vec3d startL = getMuzzlePos(WeaponSpec.MuzzleLocator.LEFT_SUB_MUZZLE);
        Vec3d startR = getMuzzlePos(WeaponSpec.MuzzleLocator.RIGHT_SUB_MUZZLE);

        Vec3d dirL = target.getEyePos().subtract(startL).normalize();
        Vec3d dirR = target.getEyePos().subtract(startR).normalize();

        spawnShell(sw, target, startL, dirL, -1);
        spawnShell(sw, target, startR, dirR, +1);
    }

    private void spawnShell(ServerWorld sw, LivingEntity target, Vec3d start, Vec3d dir, int curveSign) {
        GunTrainShellEntity shell = new GunTrainShellEntity(
                com.licht_meilleur.blue_student.registry.ModEntities.GUN_TRAIN_SHELL,
                sw
        ).setOwnerUuid(ownerPlayerUuid)
                .setTarget(target)
                .setCurveSign(curveSign);

        shell.setPosition(start.x, start.y, start.z);

        double speed = 1.25;
        shell.setVelocity(dir.normalize().multiply(speed));

        sw.spawnEntity(shell);
    }

    private LivingEntity findTarget(ServerWorld sw) {
        double r = this.mergedMode ? 64.0 : 18.0; // ★合体中は広げる（好みで 48～96）
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

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        super.updatePassengerPosition(passenger, updater);

        Vec3d seat = getSeatWorldGun();
        float y = this.getYaw();

        passenger.refreshPositionAndAngles(seat.x, seat.y, seat.z, y, 0.0f);
        passenger.prevYaw = y;
        passenger.setYaw(y);

        if (passenger instanceof LivingEntity le) {
            le.bodyYaw = y;
            le.headYaw = y;
            le.prevBodyYaw = y;
            le.prevHeadYaw = y;
        }

        passenger.noClip = true;               // ★これが効く（ブロック衝突で降ろされにくくなる）
        passenger.setNoGravity(true);          // ★落下や段差でズレにくい
        passenger.setVelocity(Vec3d.ZERO);
        passenger.velocityModified = true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("OwnerP")) ownerPlayerUuid = nbt.getUuid("OwnerP");
        if (nbt.containsUuid("Train")) trainUuid = nbt.getUuid("Train");
        if (nbt.containsUuid("Passenger")) passengerStudentUuid = nbt.getUuid("Passenger");
        fireCooldown = nbt.getInt("FireCd");
        lifeTicks = nbt.getInt("Life");

        if (nbt.contains("Ax")) {
            anchorPos = new Vec3d(nbt.getDouble("Ax"), nbt.getDouble("Ay"), nbt.getDouble("Az"));
        }
        // SYNC_TARGET_EIDはNBTに無くてもOK（毎回射撃で更新される）
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerPlayerUuid != null) nbt.putUuid("OwnerP", ownerPlayerUuid);
        if (trainUuid != null) nbt.putUuid("Train", trainUuid);
        if (passengerStudentUuid != null) nbt.putUuid("Passenger", passengerStudentUuid);
        nbt.putInt("FireCd", fireCooldown);
        nbt.putInt("Life", lifeTicks);

        if (anchorPos != null) {
            nbt.putDouble("Ax", anchorPos.x);
            nbt.putDouble("Ay", anchorPos.y);
            nbt.putDouble("Az", anchorPos.z);
        }
    }

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

    private static final Vec3d SEAT_PX   = new Vec3d(0.4, 13.4, 6.6);
    private static final Vec3d SEAT_TUNE = new Vec3d(0.0, 0.0, -12.0);

    private Vec3d getSeatWorldGun() {
        return locatorPxToWorld(SEAT_PX.add(SEAT_TUNE), this.getYaw(), this.getPos());
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

    private void discardAndNotify(ServerWorld sw) {
        if (passengerStudentUuid != null) {
            Entity p = sw.getEntity(passengerStudentUuid);
            if (p instanceof HikariEntity h) {
                h.startGunTrainCooldown();
            }
        }
        this.discard();
    }

    // GoGoGunTrainEntity 内
    private static final double FOLLOW_BACK  = 2.4;  // 後ろ距離
    private static final double FOLLOW_RIGHT = 0.0;  // 横
    private static final double FOLLOW_UP    = 0.0;  // 高さ

    private void tickFollowTrain(ServerWorld sw) {
        if (!mergedMode) return;
        if (trainUuid == null) return;

        Entity t = sw.getEntity(trainUuid);
        if (!(t instanceof GoGoTrainEntity train) || !train.isAlive()) return;

        float yaw = train.getYaw();
        Vec3d forward = forwardFromYaw(yaw).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        Vec3d base = train.getPos();
        Vec3d pos = base
                .add(0, FOLLOW_UP, 0)
                .add(forward.multiply(-FOLLOW_BACK))
                .add(right.multiply(FOLLOW_RIGHT));

        // ★テレポ系APIを避ける（これがブレの主因）
        this.setPosition(pos.x, pos.y, pos.z);
        this.setYaw(MathHelper.wrapDegrees(yaw));
        this.setPitch(0f);

        // 速度はゼロ固定
        this.setVelocity(Vec3d.ZERO);
        this.velocityModified = true;

        // ★同期値（モデル用）
        this.dataTracker.set(SYNC_BODY_YAW, MathHelper.wrapDegrees(yaw));

        // prev を潰して補間のブレを減らす
        this.prevX = this.getX();
        this.prevY = this.getY();
        this.prevZ = this.getZ();
        this.prevYaw = this.getYaw();
    }

}