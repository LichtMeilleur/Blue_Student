package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.projectile.StudentBulletEntity;
import com.licht_meilleur.blue_student.network.ServerFx;
import com.licht_meilleur.blue_student.student.StudentId;
import com.licht_meilleur.blue_student.weapon.ProjectileWeaponAction;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class ShirokoDroneEntity extends Entity implements GeoEntity {

    // ===== animation names =====
    public static final String ANIM_DRONE       = "animation.model.drone";

    private static final RawAnimation DRONE_LOOP  = RawAnimation.begin().thenLoop(ANIM_DRONE);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);


    private int lifeTicks = 20 * 20; // 20秒

    // ===== tracked =====
    private static final TrackedData<Integer> START_TRIGGER =
            DataTracker.registerData(ShirokoDroneEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // owner + target
    private UUID ownerUuid;
    private UUID forcedTargetUuid; // 任意：シロコが指定する場合

    // follow tuning
    private static final Vec3d OFFSET_LOCAL = new Vec3d(0.35, 1.15, -0.25); // 右上・少し後ろ（誤射しにくい）
    private static final double FOLLOW_LERP = 0.18; // 小さいほど「遅れ」増える（しっぽ感）
    private static final double MAX_STEP   = 0.9;   // 1tickで飛びすぎ防止

    // combat
    private int shootCooldown = 0;
    private static final int SHOOT_INTERVAL = 6; // 0.3秒
    private static final double ACQUIRE_RANGE = 18.0;

    // client anim
    private int clientStartTicks = 0;
    private int lastStartTrigger = 0;
    private static final int START_ANIM_TICKS = 22;

    private int lastOwnerShotTrigger = 0;


    public ShirokoDroneEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public ShirokoDroneEntity(World world) {
        this(BlueStudentMod.SHIROKO_DRONE, world);
    }

    public ShirokoDroneEntity setOwnerUuid(UUID owner) {
        this.ownerUuid = owner;
        return this;
    }

    public ShirokoDroneEntity setTargetUuid(@Nullable UUID target) {
        this.forcedTargetUuid = target;
        return this;
    }

    public void requestStartAnim() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(START_TRIGGER, this.dataTracker.get(START_TRIGGER) + 1);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(START_TRIGGER, 0);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        if (nbt.containsUuid("Target")) forcedTargetUuid = nbt.getUuid("Target");
        shootCooldown = nbt.getInt("ShootCd");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        if (forcedTargetUuid != null) nbt.putUuid("Target", forcedTargetUuid);
        nbt.putInt("ShootCd", shootCooldown);
    }

    @Override
    public void tick() {
        super.tick();


        if (!this.getWorld().isClient) {
            if (--lifeTicks <= 0) {
                this.discard();
                return;
            }
        }

        // ===== client anim =====
        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(START_TRIGGER);
            if (trig != lastStartTrigger) {
                lastStartTrigger = trig;
                clientStartTicks = START_ANIM_TICKS;
            } else if (clientStartTicks > 0) {
                clientStartTicks--;
            }
            return;
        }

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        Entity owner = (ownerUuid != null) ? sw.getEntity(ownerUuid) : null;
        if (!(owner instanceof AbstractStudentEntity shiroko) || !owner.isAlive()) {
            this.discard();
            return;
        }

        // いまの速度
        Vec3d vel = this.getVelocity();

        // ===== follow (spring) =====
        Vec3d desired = calcDesiredPos(shiroko); // ← 今のまま使ってOK（斜め上の基準点）
        Vec3d cur = this.getPos();
        Vec3d to = desired.subtract(cur);
        Vec3d nextPos = cur.add(vel);
        this.setPosition(nextPos.x, nextPos.y, nextPos.z);



// スプリング係数（好みで調整）
        double k = 0.18;        // 引っ張り強さ（0.10〜0.30目安）
        double damping = 0.65;  // 減衰（0.45〜0.85目安）

// 加速度 = (目標への引力) - (速度の抵抗)
        Vec3d accel = to.multiply(k).subtract(vel.multiply(damping));

// 速度更新
        vel = vel.add(accel);

// 速度上限（暴走防止）
        double maxSpeed = 0.35;
        if (vel.lengthSquared() > maxSpeed * maxSpeed) {
            vel = vel.normalize().multiply(maxSpeed);
        }

// 適用（Positionは触らない！）
        this.setVelocity(vel);
        this.velocityModified = true;


        // 向き
        LivingEntity tgt = shiroko.getTarget();
        if (tgt != null && tgt.isAlive()) {
            Vec3d from = this.getPos();
            to = tgt.getEyePos();
            Vec3d look = to.subtract(from);

            double dx = look.x;
            double dy = look.y;
            double dz = look.z;

            float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

            this.setYaw(yaw);
            this.setPitch(pitch);
            this.prevYaw = yaw;
            this.prevPitch = pitch;
        } else {
            // 敵がいない時はシロコと同じ向き
            this.setYaw(shiroko.getYaw());
            this.setPitch(0);
            this.prevYaw = this.getYaw();
            this.prevPitch = this.getPitch();
        }


        // ===== sync fire with owner =====
        int trig = shiroko.getShotTrigger(); // ★ シロコの射撃トリガー読む

        if (trig != lastOwnerShotTrigger) {
            lastOwnerShotTrigger = trig;

            shootFromDrone(sw, shiroko);
        }


    }

    private Vec3d calcDesiredPos(AbstractStudentEntity owner) {

        Vec3d forward = owner.getRotationVec(1.0f).normalize();

        return owner.getPos()
                .add(0, 2.0, 0)        // 頭上2ブロック
                .add(forward.multiply(-0.8)); // 少し後ろ
    }

    private float approachAngle(float cur, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - cur);
        delta = MathHelper.clamp(delta, -maxStep, maxStep);
        return cur + delta;
    }


    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::predicate));
    }

    private <T extends GeoEntity> PlayState predicate(AnimationState<T> state) {

        state.getController().setAnimation(DRONE_LOOP);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
    private void shootFromDrone(ServerWorld sw, AbstractStudentEntity owner) {
        LivingEntity target = owner.getTarget();
        if (target == null || !target.isAlive()) return;

        WeaponSpec spec = WeaponSpecs.forStudent(StudentId.SHIROKO);

        Vec3d start = this.getPos(); // ← ドローン実位置を推奨（追従遅れが見た目に一致）
        Vec3d dir = target.getEyePos().subtract(start).normalize();

        // 1) 弾はシロコowner（ダメージ責任はシロコ）
        new ProjectileWeaponAction().shootFromCustomPos(
                owner,
                target,
                spec,
                start,
                dir
        );

        // 2) FXはドローンIDで送る（muzzle補正されない）
        ServerFx.sendShotFx(
                sw,
                this.getId(),     // ★ここが「ドローンだけドローンID」
                start,
                spec.fxType,
                spec.fxWidth,
                new Vec3d[]{dir},
                (float) spec.range
        );
    }


}
