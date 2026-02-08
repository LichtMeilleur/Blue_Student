package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class KisakiDragonEntity extends Entity implements GeoEntity {

    // 0: FLY(run) / 1: COIL(buff)
    private static final TrackedData<Integer> STATE =
            DataTracker.registerData(KisakiDragonEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int STATE_FLY  = 0;
    private static final int STATE_COIL = 1;

    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation BUFF = RawAnimation.begin().thenLoop("buff");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private UUID ownerKisakiUuid;
    @Nullable private UUID targetUuid;

    // coil演出用
    private int coilTicks = 0;
    private double coilAngle = 0.0;

    public KisakiDragonEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    /** スポーン直後にGoalから設定 */
    public KisakiDragonEntity setOwnerAndTarget(@Nullable UUID ownerKisaki, @Nullable UUID target) {
        this.ownerKisakiUuid = ownerKisaki;
        this.targetUuid = target;
        return this;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STATE, STATE_FLY);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        ownerKisakiUuid = nbt.containsUuid("OwnerKisaki") ? nbt.getUuid("OwnerKisaki") : null;
        targetUuid = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
        if (nbt.contains("State")) this.dataTracker.set(STATE, nbt.getInt("State"));
        coilTicks = nbt.getInt("CoilTicks");
        coilAngle = nbt.getDouble("CoilAngle");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerKisakiUuid != null) nbt.putUuid("OwnerKisaki", ownerKisakiUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        nbt.putInt("State", this.dataTracker.get(STATE));
        nbt.putInt("CoilTicks", coilTicks);
        nbt.putDouble("CoilAngle", coilAngle);
    }

    @Override
    public void tick() {
        super.tick();

        // クライアントは描画だけでOK（位置はサーバー同期）
        if (this.getWorld().isClient) return;

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // ターゲット取得
        Entity target = (targetUuid != null) ? sw.getEntity(targetUuid) : null;
        if (target == null || !target.isAlive()) {
            this.discard();
            return;
        }

        int st = this.dataTracker.get(STATE);

        // target基準位置（巻きつき中心）
        Vec3d center = target.getPos().add(0, target.getHeight() * 0.6, 0);

        if (st == STATE_FLY) {
            // キサキから target へ “飛ぶ”
            Vec3d to = center.subtract(this.getPos());
            double d = to.length();
            lookAt(target);


            // 到達したら巻きつきへ
            if (d < 1.2) {
                this.dataTracker.set(STATE, STATE_COIL);
                coilTicks = 40;         // buff演出時間（2秒）
                coilAngle = 0.0;
                return;
            }

            // 速度（適当に調整）
            Vec3d step = to.normalize().multiply(0.65);
            Vec3d next = this.getPos().add(step);

            // 物理衝突させたくないので setPosition 系でOK
            this.setPosition(next.x, next.y, next.z);
            this.setVelocity(Vec3d.ZERO);

        } else {
            // 巻きつき：target の周囲を円運動
            coilTicks--;
            // ★位置固定（ターゲット横にピタ止め）
            Vec3d p = center.add(0.8, 0.0, 0.0); // 横に少しずらすだけ

            this.setPosition(p.x, p.y, p.z);
            lookAt(target);

            this.setVelocity(Vec3d.ZERO);

            // 寿命
            if (coilTicks <= 0) {
                this.discard();
            }
        }
    }

    // 物理無効化（念のため）
    @Override
    public void move(MovementType movementType, Vec3d movement) {
        // 何もしない（位置は tick で setPosition）
    }

    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<KisakiDragonEntity> state) {
        int st = this.dataTracker.get(STATE);
        state.getController().setAnimation(st == STATE_COIL ? BUFF : RUN);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private void lookAt(Entity target) {
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0)
                .subtract(this.getPos());

        double yaw = Math.toDegrees(Math.atan2(-to.x, to.z));

        this.setYaw((float) yaw);
        this.setBodyYaw((float) yaw);
        this.setHeadYaw((float) yaw);
    }

}
