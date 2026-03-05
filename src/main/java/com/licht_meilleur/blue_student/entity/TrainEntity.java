package com.licht_meilleur.blue_student.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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

public class TrainEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===== リンク情報 =====
    private UUID ownerPlayerUuid;
    private UUID gunTrainUuid;
    private UUID targetUuid;
    private UUID nozomiPassengerUuid;

    // ===== モード（★ここが肝）=====
    public enum TrainMode {
        SINGLE_CHARGE,      // 単体：突撃（1回）だけ
        COMBO_CYCLE         // 合体：巡航5秒→突撃→巡航5秒… を繰り返す
    }

    private TrainMode mode = TrainMode.SINGLE_CHARGE;
    public TrainMode getMode() { return mode; }
    public void setMode(TrainMode mode) { this.mode = mode; }

    // ===== 合体時サイクル制御 =====
    private int phaseTicks = 0;
    private boolean gunFireEnabled = true; // ★突撃中はfalseにする
    public boolean isGunFireEnabled() { return gunFireEnabled; }

    private static final int CRUISE_TICKS = 20 * 5; // 5秒
    private static final int CHARGE_TICKS = 20 * 1; // 突撃持続（演出用。到達で早期終了）
    private static final double CRUISE_SPEED = 0.35;
    private static final double CHARGE_SPEED = 1.25;

    // ===== 合体巡航（円運動）パラメータ =====
    private float theta = 0f;
    private float radius = 6.0f;
    private float omega = 0.12f;
    private boolean clockwise = true;


    // ===== 突撃ヒット（ノゾミスキル）パラメータ =====
    private static final float CHARGE_HIT_RADIUS = 1.6f;   // 当たり判定（半径）
    private static final float CHARGE_DAMAGE     = 8.0f;   // ダメージ
    private static final double KNOCKBACK_H      = 1.2;    // 横吹き飛ばし
    private static final double KNOCKBACK_Y      = 0.25;   // 縦吹き飛ばし
    private static final int SINGLE_CHARGE_MIN_TICKS = 6;  // 発生直後の誤爆防止


    public TrainEntity setClockwise(boolean clockwise) { this.clockwise = clockwise; return this; }

    // ===== 寿命 =====
    private int lifeTicks = 0;
    private static final int MAX_LIFE = 20 * 15; // 15秒（好み）

    // ===== yaw同期 =====
    private static final TrackedData<Float> SYNC_YAW =
            DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public TrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true; // 合体/単体どっちも埋まり防止で true 推奨
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SYNC_YAW, 0.0f);
    }

    public TrainEntity setOwnerPlayerUuid(UUID ownerPlayerUuid) { this.ownerPlayerUuid = ownerPlayerUuid; return this; }
    public TrainEntity setGunTrainUuid(UUID gunUuid) { this.gunTrainUuid = gunUuid; return this; }
    public TrainEntity setTargetUuid(UUID target) { this.targetUuid = target; return this; }
    public TrainEntity setNozomiPassengerUuid(UUID id) { this.nozomiPassengerUuid = id; return this; }

    public UUID getOwnerPlayerUuid() { return ownerPlayerUuid; }
    public UUID getGunTrainUuid() { return gunTrainUuid; }
    public UUID getTargetUuid() { return targetUuid; }

    private void setYawStableServer(float yaw) {
        float y = MathHelper.wrapDegrees(yaw);
        this.prevYaw = y;
        this.setYaw(y);
        this.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), y, 0.0f);
        this.dataTracker.set(SYNC_YAW, y);
    }

    @Override
    public void tick() {
        super.tick();

        // クライアント：同期Yawを適用
        if (this.getWorld().isClient) {
            float y = this.dataTracker.get(SYNC_YAW);
            this.prevYaw = y;
            this.setYaw(y);
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // 寿命・owner不在なら畳む
        if (++lifeTicks > MAX_LIFE) { discardWithLinked(sw); return; }
        if (!isOwnerAlive(sw))      { discardWithLinked(sw); return; }


        Entity target = (targetUuid != null) ? sw.getEntity(targetUuid) : null;
        if (target == null || !target.isAlive()) {
            // ターゲット無しなら停止（必要なら畳むでもOK）
            this.setVelocity(Vec3d.ZERO);
            return;
        }

        // ★単体版では GunTrain 連結はしない（合体は別Entityでやる）
        gunTrainUuid = null;
        gunFireEnabled = false;

        tickSingleCharge(sw, target);
    }

    // ==============================
    // 単体：突撃（1回）だけ
    // ==============================
    private void tickSingleCharge(ServerWorld sw, Entity target) {
        // 発生直後の誤爆防止（スポーンした瞬間に近接判定を拾うのを避ける）
        if (lifeTicks > SINGLE_CHARGE_MIN_TICKS) {
            if (tryChargeHit(sw)) {
                // 1回当たったら終了（単体は“戻らない”）
                endSkillAndDiscard(sw);
                return;
            }
        }

        Vec3d from = this.getPos();
        Vec3d to   = target.getPos().add(0, 0.2, 0);
        Vec3d d    = to.subtract(from);

        // ターゲット到達でも終了
        if (d.lengthSquared() < 0.45) {
            endSkillAndDiscard(sw);
            return;
        }

        Vec3d v = d.normalize().multiply(CHARGE_SPEED);
        this.setVelocity(v);
        this.move(MovementType.SELF, v);

        if (v.horizontalLengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
            setYawStableServer(yaw);
        }
    }

    // ==============================
    // 合体：巡航5秒→突撃→巡航5秒…
    // ==============================
    private void tickComboCycle(ServerWorld sw, Entity target) {
        // phaseTicks は「今のフェーズの残り」
        // 0になったらフェーズ切替
        if (phaseTicks <= 0) {
            // フェーズ切替
            // gunFireEnabled が true なら今から「突撃フェーズ」に入る、falseなら「巡航」に戻す
            if (gunFireEnabled) {
                // 次：突撃（射撃停止）
                gunFireEnabled = false;
                phaseTicks = CHARGE_TICKS;
            } else {
                // 次：巡航（射撃再開）
                gunFireEnabled = true;
                phaseTicks = CRUISE_TICKS;
            }
        }

        // フェーズ処理
        if (gunFireEnabled) {
            // 巡航：円運動（合体時のみ）
            tickCruiseOrbit(sw, target);
        } else {
            // 突撃：射撃停止、一直線
            tickCharge(sw, target);
        }

        phaseTicks--;
    }

    private void tickCruiseOrbit(ServerWorld sw, Entity center) {
        float step = Math.abs(omega);
        theta += clockwise ? +step : -step;

        double cx = center.getX();
        double cz = center.getZ();

        double gx = cx + Math.cos(theta) * radius;
        double gz = cz + Math.sin(theta) * radius;
        double gy = center.getY(); // 高さは中心に合わせる（好みでHeightmapにしてもOK）

        Vec3d goal = new Vec3d(gx, gy, gz);
        Vec3d dir = goal.subtract(this.getPos());

        if (dir.lengthSquared() > 1e-6) {
            this.setVelocity(dir.normalize().multiply(CRUISE_SPEED));
        } else {
            this.setVelocity(Vec3d.ZERO);
        }

        this.move(MovementType.SELF, this.getVelocity());

        Vec3d v = this.getVelocity();
        if (v.horizontalLengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
            setYawStableServer(yaw);
        }
    }

    private void tickCharge(ServerWorld sw, Entity target) {
        Vec3d from = this.getPos();
        Vec3d to   = target.getPos().add(0, 0.2, 0);
        Vec3d d    = to.subtract(from);

        if (lifeTicks > SINGLE_CHARGE_MIN_TICKS) {
            if (tryChargeHit(sw)) {
                // 合体時は「当たったら突撃フェーズ終了」だけでOK
                phaseTicks = 0;
                return;
            }
        }

        // 合体突撃は「到達でフェーズ終了」させてOK
        if (d.lengthSquared() < 0.65) {
            phaseTicks = 0; // 次tickで巡航へ
            return;
        }

        Vec3d v = d.normalize().multiply(CHARGE_SPEED);
        this.setVelocity(v);
        this.move(MovementType.SELF, v);

        if (v.horizontalLengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
            setYawStableServer(yaw);
        }
    }

    private void dropNozomiIfAny(ServerWorld sw) {
        if (nozomiPassengerUuid == null) return;
        Entity p = sw.getEntity(nozomiPassengerUuid);
        if (p != null && p.getVehicle() == this) p.stopRiding();
    }

    private void discardWithLinked(ServerWorld sw) {
        if (gunTrainUuid != null) {
            Entity e = sw.getEntity(gunTrainUuid);
            if (e != null) e.discard();
        }
        endSkillAndDiscard(sw);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        float bodyYaw = this.getYaw();

        if (passenger instanceof NozomiEntity) {
            Vec3d seat = getSeatWorldNozomiOnTrain();
            passenger.refreshPositionAndAngles(seat.x, seat.y, seat.z, bodyYaw, 0.0f);

            passenger.prevYaw = bodyYaw;
            passenger.setYaw(bodyYaw);

            if (passenger instanceof LivingEntity le) {
                le.bodyYaw = bodyYaw;
                le.headYaw = bodyYaw;
                le.prevBodyYaw = bodyYaw;
                le.prevHeadYaw = bodyYaw;
            }

            passenger.noClip = true;
            passenger.setNoGravity(true);
            passenger.setVelocity(Vec3d.ZERO);
            passenger.velocityModified = true;
        } else {
            // それ以外は親の標準処理
            super.updatePassengerPosition(passenger, updater);
        }
    }

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

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerPlayerUuid != null) nbt.putUuid("OwnerP", ownerPlayerUuid);
        if (gunTrainUuid != null) nbt.putUuid("Gun", gunTrainUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        if (nozomiPassengerUuid != null) nbt.putUuid("NozomiP", nozomiPassengerUuid);

        nbt.putString("Mode", mode.name());
        nbt.putInt("Life", lifeTicks);
        nbt.putInt("Phase", phaseTicks);
        nbt.putBoolean("GunFire", gunFireEnabled);

        nbt.putFloat("Theta", theta);
        nbt.putFloat("Radius", radius);
        nbt.putFloat("Omega", omega);
        nbt.putBoolean("Clockwise", clockwise);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("OwnerP")) ownerPlayerUuid = nbt.getUuid("OwnerP");
        if (nbt.containsUuid("Gun")) gunTrainUuid = nbt.getUuid("Gun");
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");
        if (nbt.containsUuid("NozomiP")) nozomiPassengerUuid = nbt.getUuid("NozomiP");

        if (nbt.contains("Mode")) {
            try { mode = TrainMode.valueOf(nbt.getString("Mode")); }
            catch (Exception ignored) { mode = TrainMode.SINGLE_CHARGE; }
        }
        lifeTicks = nbt.getInt("Life");
        phaseTicks = nbt.getInt("Phase");
        gunFireEnabled = nbt.getBoolean("GunFire");

        theta = nbt.getFloat("Theta");
        radius = nbt.getFloat("Radius");
        omega = nbt.getFloat("Omega");
        clockwise = nbt.getBoolean("Clockwise");
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

    private void endSkillAndDiscard(ServerWorld sw) {
        // ノゾミを降ろす
        dropNozomiIfAny(sw);

        // クールタイム開始＆アニメ解除
        if (nozomiPassengerUuid != null) {
            Entity p = sw.getEntity(nozomiPassengerUuid);
            if (p instanceof NozomiEntity n && n.isAlive()) {
                n.setTrainSkillActive(false);
                n.startTrainCooldown();
            }
        }
        this.discard();
    }

    private boolean tryChargeHit(ServerWorld sw) {
        // ★ Train自身の bounding box を使わない。位置中心の Box を作る
        Vec3d c = this.getPos();
        Box box = new Box(c, c).expand(CHARGE_HIT_RADIUS, 1.6, CHARGE_HIT_RADIUS);

        var list = sw.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class, box, LivingEntity::isAlive);
        if (list.isEmpty()) return false;

        // 一番近い敵
        net.minecraft.entity.mob.HostileEntity best = null;
        double bestD2 = 1e18;
        for (var h : list) {
            // 乗客(ノゾミ)を巻き込まない（Hostile限定なら不要だけど保険）
            if (nozomiPassengerUuid != null && h.getUuid().equals(nozomiPassengerUuid)) continue;

            double d2 = h.squaredDistanceTo(c);
            if (d2 < bestD2) { bestD2 = d2; best = h; }
        }
        if (best == null) return false;

        // ★ダメージ（magicで無効化されるケースがあるので generic に）
        best.damage(sw.getDamageSources().generic(), CHARGE_DAMAGE);

        // ★ノックバック：takeKnockback を使う（addVelocityより反映が確実）
        Vec3d push = best.getPos().subtract(c);
        if (push.horizontalLengthSquared() < 1.0e-6) push = new Vec3d(0, 0, 1);
        Vec3d dir = new Vec3d(push.x, 0, push.z).normalize();

        best.takeKnockback((float)KNOCKBACK_H, -dir.x, -dir.z); // takeKnockbackは「押される向き」の指定が直感と逆になりがち
        best.addVelocity(0.0, KNOCKBACK_Y, 0.0);
        best.velocityModified = true;

        return true;
    }
    // Trainの前方/右方向ベクトル
    private static Vec3d forwardFromYaw(float yawDeg) {
        float r = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(r), 0, MathHelper.cos(r));
    }

    private Vec3d getSeatWorldNozomiOnTrain() {
        Vec3d forward = forwardFromYaw(this.getYaw()).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // ★ノゾミ座席：中央ちょい上・少し後ろ（好みで調整）
        return this.getPos()
                .add(0, 0.90, 0)
                .add(forward.multiply(-0.10))
                .add(right.multiply(0.00));
    }

    private Vec3d getSeatWorldGunOnTrain() {
        Vec3d forward = forwardFromYaw(this.getYaw()).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // ★GunTrain座席：Trainの右側に配置（重なり防止）
        // ここを調整すると「重なってる」が一発で直ります
        return this.getPos()
                .add(0, 0.65, 0)
                .add(forward.multiply(0.05))
                .add(right.multiply(+1.10));
    }

}