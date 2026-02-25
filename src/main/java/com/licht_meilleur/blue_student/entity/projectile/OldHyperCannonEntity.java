package com.licht_meilleur.blue_student.entity.projectile;

import com.licht_meilleur.blue_student.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

public class OldHyperCannonEntity extends Entity implements GeoEntity {

    public enum Side { LEFT, RIGHT }

    // ---- tuning ----
    public static final int LIFE_TICKS = 20;
    public static final int HIT_INTERVAL = 2;
    public static final float DAMAGE_PER_HIT = 4.0f; // 3～5の中間
    public static final double MAX_RANGE = 24.0;
    public static final double RADIUS = 0.7; // “太いビーム”判定

    // ---- DataTracker（クライアント描画用）----
    private static final TrackedData<Float> BEAM_LEN =
            DataTracker.registerData(OldHyperCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LIFE =
            DataTracker.registerData(OldHyperCannonEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // 終点を同期（Vec3dはTrackedDataに入れられないので3分割）
    private static final TrackedData<Float> END_X =
            DataTracker.registerData(OldHyperCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> END_Y =
            DataTracker.registerData(OldHyperCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> END_Z =
            DataTracker.registerData(OldHyperCannonEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---- server state ----
    private UUID ownerUuid;
    private UUID targetUuid;
    private Side side = Side.LEFT;

    private int ageTicks = 0;

    public OldHyperCannonEntity(EntityType<? extends OldHyperCannonEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }
/*
    public OldHyperCannonEntity(World world) {
        this(ModEntities.HYPER_CANNON, world);
    }
*/
    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(BEAM_LEN, 0.0f);
        this.dataTracker.startTracking(LIFE, LIFE_TICKS);

        this.dataTracker.startTracking(END_X, 0.0f);
        this.dataTracker.startTracking(END_Y, 0.0f);
        this.dataTracker.startTracking(END_Z, 0.0f);
    }

    // Goal から1回だけ呼ぶ
    public void init(LivingEntity owner, LivingEntity target, Side side) {
        this.ownerUuid = owner.getUuid();
        this.targetUuid = target.getUuid();
        this.side = (side == null) ? Side.LEFT : side;

        this.ageTicks = 0;
        this.dataTracker.set(LIFE, LIFE_TICKS);

        // 初期同期（とりあえず目線）
        Vec3d start = startFrom(owner, target);
        Vec3d end = target.getEyePos();
        this.setPos(start.x, start.y, start.z);
        setEndTracked(end);
        this.dataTracker.set(BEAM_LEN, (float) start.distanceTo(end));
        syncYawPitchFromSegment(start, end);
    }

    // client側用（rendererが使う）
    public float getBeamLengthClient() {
        return this.dataTracker.get(BEAM_LEN);
    }

    public Vec3d getEndClient() {
        return new Vec3d(this.dataTracker.get(END_X), this.dataTracker.get(END_Y), this.dataTracker.get(END_Z));
    }

    private void setEndTracked(Vec3d end) {
        this.dataTracker.set(END_X, (float) end.x);
        this.dataTracker.set(END_Y, (float) end.y);
        this.dataTracker.set(END_Z, (float) end.z);
    }

    @Override
    public void tick() {
        super.tick();

        // client: 位置/向きは DataTracker と自分のposから計算して renderer が使える
        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        ageTicks++;

        int lifeLeft = this.dataTracker.get(LIFE) - 1;
        this.dataTracker.set(LIFE, lifeLeft);
        if (lifeLeft <= 0) {
            this.discard();
            return;
        }

        LivingEntity owner = getOwnerLiving(sw);
        LivingEntity target = getTargetLiving(sw);

        if (owner == null || !owner.isAlive() || target == null || !target.isAlive()) {
            this.discard();
            return;
        }

        // ===== 始点 = SUB_L / SUB_R（近似）=====
        Vec3d start = startFrom(owner, target);

        // ===== 終点 = target目線（壁で止める）=====
        Vec3d wantedEnd = target.getEyePos();
        Vec3d clippedEnd = clipByBlocks(sw, owner, start, wantedEnd);

        // 同期
        // 同期（位置＋回転を“まとめて”更新してネット同期を確実にする）
        float[] yp = calcYawPitch(start, clippedEnd);
        this.refreshPositionAndAngles(start.x, start.y, start.z, yp[0], yp[1]);

        double len = start.distanceTo(clippedEnd);
        this.dataTracker.set(BEAM_LEN, (float) len);

// 終点は DataTracker に入れて renderer が使う
        setEndTracked(clippedEnd);

        // ★重要：向きを「start→end」にする（真横バグの根治）
        syncYawPitchFromSegment(start, clippedEnd);

        // 2tickごとに多段ヒット
        if ((ageTicks % HIT_INTERVAL) == 0) {
            damageAlongSegment(sw, owner, start, clippedEnd);
        }
    }

    private LivingEntity getOwnerLiving(ServerWorld sw) {
        if (ownerUuid == null) return null;
        Entity e = sw.getEntity(ownerUuid);
        return (e instanceof LivingEntity le) ? le : null;
    }

    private LivingEntity getTargetLiving(ServerWorld sw) {
        if (targetUuid == null) return null;
        Entity e = sw.getEntity(targetUuid);
        return (e instanceof LivingEntity le) ? le : null;
    }

    private Vec3d startFrom(LivingEntity owner, LivingEntity target) {
        // 「SUB_L / SUB_R」をサーバーで完全再現できないので、ワールド座標の近似で作る
        Vec3d base = owner.getEyePos().subtract(0, 0.10, 0);

        Vec3d toT = target.getEyePos().subtract(base);
        Vec3d forward = toT.lengthSquared() < 1e-6 ? owner.getRotationVec(1.0f) : toT.normalize();

        // right = up x forward
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(forward);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        double sign = (this.side == Side.LEFT) ? -1.0 : 1.0;
        double sideOffset = 0.22 * sign;
        double fwdOffset = 0.12;

        return base.add(right.multiply(sideOffset)).add(forward.multiply(fwdOffset));
    }

    private Vec3d clipByBlocks(ServerWorld sw, LivingEntity owner, Vec3d start, Vec3d end) {
        // 長すぎたら最大距離でカット
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len > MAX_RANGE && len > 1e-6) {
            end = start.add(dir.multiply(MAX_RANGE / len));
        }

        HitResult hr = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                owner
        ));

        return (hr.getType() == HitResult.Type.BLOCK) ? hr.getPos() : end;
    }

    private void syncYawPitchFromSegment(Vec3d start, Vec3d end) {
        Vec3d d = end.subtract(start);
        double dx = d.x;
        double dy = d.y;
        double dz = d.z;

        double h = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float)(MathHelper.atan2(dx, dz) * (180.0 / Math.PI));
        float pitch = (float)(-(MathHelper.atan2(dy, h) * (180.0 / Math.PI)));

        this.setYaw(yaw);
        this.setPitch(pitch);

        this.prevYaw = yaw;
        this.prevPitch = pitch;
    }

    private void damageAlongSegment(ServerWorld sw, LivingEntity owner, Vec3d start, Vec3d end) {
        Box box = new Box(start, end).expand(RADIUS);

        List<LivingEntity> candidates = sw.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != owner
        );

        DamageSource src = sw.getDamageSources().magic();

        for (LivingEntity target : candidates) {
            double distSq = distanceSqPointToSegment(target.getPos().add(0, target.getHeight() * 0.5, 0), start, end);
            if (distSq > RADIUS * RADIUS) continue;

            if (!hasLineOfSightNoFluid(sw, start, target.getPos().add(0, target.getHeight() * 0.5, 0), owner)) continue;

            Vec3d beforeVel = target.getVelocity();
            boolean ok = target.damage(src, DAMAGE_PER_HIT);

            // ノックバック抑制
            if (ok) {
                target.setVelocity(beforeVel);
                target.velocityDirty = true;
            }
        }
    }

    private boolean hasLineOfSightNoFluid(ServerWorld sw, Vec3d from, Vec3d to, LivingEntity owner) {
        HitResult hr = sw.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                owner
        ));
        return hr.getType() == HitResult.Type.MISS;
    }

    private static double distanceSqPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < 1.0e-9) return p.squaredDistanceTo(a);

        double t = p.subtract(a).dotProduct(ab) / abLenSq;
        t = MathHelper.clamp(t, 0.0, 1.0);
        Vec3d proj = a.add(ab.multiply(t));
        return p.squaredDistanceTo(proj);
    }

    // ---- NBT ----
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
        if (nbt.containsUuid("Target")) this.targetUuid = nbt.getUuid("Target");
        if (nbt.contains("Side")) this.side = Side.valueOf(nbt.getString("Side"));

        this.ageTicks = nbt.getInt("AgeTicks");
        this.dataTracker.set(BEAM_LEN, nbt.getFloat("BeamLen"));
        this.dataTracker.set(LIFE, nbt.getInt("Life"));

        this.dataTracker.set(END_X, nbt.getFloat("EndX"));
        this.dataTracker.set(END_Y, nbt.getFloat("EndY"));
        this.dataTracker.set(END_Z, nbt.getFloat("EndZ"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        if (side != null) nbt.putString("Side", side.name());

        nbt.putInt("AgeTicks", ageTicks);
        nbt.putFloat("BeamLen", this.dataTracker.get(BEAM_LEN));
        nbt.putInt("Life", this.dataTracker.get(LIFE));

        nbt.putFloat("EndX", this.dataTracker.get(END_X));
        nbt.putFloat("EndY", this.dataTracker.get(END_Y));
        nbt.putFloat("EndZ", this.dataTracker.get(END_Z));
    }

    // ---- GeckoLib ----
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this, "beam", 0,
                state -> {
                    // 最初だけ伸びる→以降維持
                    if (ageTicks <= 2) {
                        state.setAnimation(RawAnimation.begin().thenPlay("animation.beam"));
                    } else {
                        state.setAnimation(RawAnimation.begin().thenLoop("animation.beam_loop"));
                    }
                    return PlayState.CONTINUE;
                }
        ));
    }
    private static float[] calcYawPitch(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double dx = d.x, dy = d.y, dz = d.z;

        double yaw = MathHelper.atan2(dx, dz) * (180.0 / Math.PI);   // 注意：x,z順
        double h = Math.sqrt(dx*dx + dz*dz);
        double pitch = -(MathHelper.atan2(dy, h) * (180.0 / Math.PI));

        return new float[]{(float) yaw, (float) pitch};
    }

}