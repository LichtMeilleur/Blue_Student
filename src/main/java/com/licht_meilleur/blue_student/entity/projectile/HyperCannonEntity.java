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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class HyperCannonEntity extends Entity {

    public enum Side { LEFT, RIGHT }

    // ===== tuning =====
    public static final int LIFE_TICKS = 20;          // 照射時間
    public static final int HIT_INTERVAL = 2;         // 2tickごと
    public static final float DAMAGE_PER_HIT = 4.0f;  // 3〜5の中間（好みでrandにしてもOK）
    public static final double MAX_RANGE = 24.0;
    public static final double RADIUS = 0.7;          // 太さ（当たり判定）

    // 粒子密度（重ければ 0.8〜1.2 に上げる）
    private static final double PARTICLE_STEP = 0.6;
    private static final int PARTICLE_EVERY_TICKS = 2;

    // ===== DataTracker（寿命だけ同期しておけばOK）=====
    private static final TrackedData<Integer> LIFE =
            DataTracker.registerData(HyperCannonEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // ===== server state =====
    private UUID ownerUuid;
    private UUID targetUuid;
    private Side side = Side.LEFT;
    private int ageTicks = 0;

    public HyperCannonEntity(EntityType<? extends HyperCannonEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public HyperCannonEntity(World world) {
        this(ModEntities.HYPER_CANNON, world);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LIFE, LIFE_TICKS);
    }

    /** Goalから呼ぶ初期化 */
    public void init(LivingEntity owner, LivingEntity target, Side side) {
        this.ownerUuid = owner.getUuid();
        this.targetUuid = target.getUuid();
        this.side = side;
        this.ageTicks = 0;
        this.dataTracker.set(LIFE, LIFE_TICKS);

        // 位置はtickで更新。とりあえずownerの目あたり
        this.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        ageTicks++;

        int lifeLeft = this.dataTracker.get(LIFE) - 1;
        this.dataTracker.set(LIFE, lifeLeft);
        if (lifeLeft <= 0) {
            this.discard();
            return;
        }

        LivingEntity owner = getLiving(sw, ownerUuid);
        LivingEntity target = getLiving(sw, targetUuid);

        if (owner == null || !owner.isAlive() || target == null || !target.isAlive()) {
            this.discard();
            return;
        }

        // ===== 始点：SUB_L / SUB_R “近似” =====
        Vec3d start = startFrom(owner, target, side);

        // ===== 終点：target目線（壁で止める）=====
        Vec3d wantedEnd = target.getEyePos();
        Vec3d end = clipByBlocks(sw, owner, start, wantedEnd);

        // 自分の座標は始点に置く（描画しないので意味は薄いが、デバッグに便利）
        this.setPos(start.x, start.y, start.z);

        // ===== 2tickごとに当たり判定 =====
        if ((ageTicks % HIT_INTERVAL) == 0) {
            damageAlongSegment(sw, owner, start, end);
        }

        // ===== 粒子（線上にSonicBoomを敷く）=====
        if ((ageTicks % PARTICLE_EVERY_TICKS) == 0) {
            spawnSonicBeam(sw, start, end);
        }
    }

    // ------------------------
    // geometry helpers
    // ------------------------

    private static Vec3d startFrom(LivingEntity owner, LivingEntity target, Side side) {
        // base：目線ちょい下（銃口っぽく）
        Vec3d base = owner.getEyePos().subtract(0, 0.10, 0);

        // forward：敵方向を優先（LookAtに依存しない）
        Vec3d toT = target.getEyePos().subtract(base);
        Vec3d forward = (toT.lengthSquared() < 1e-6)
                ? owner.getRotationVec(1.0f)
                : toT.normalize();

        // right = up x forward
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(forward);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        double sign = (side == Side.LEFT) ? -1.0 : 1.0;

        double sideOff = 0.22 * sign; // 左右幅
        double fwdOff  = 0.12;        // 少し前へ

        return base.add(right.multiply(sideOff)).add(forward.multiply(fwdOff));
    }

    private static Vec3d clipByBlocks(ServerWorld sw, LivingEntity owner, Vec3d start, Vec3d wantedEnd) {
        BlockHitResult hit = sw.raycast(new RaycastContext(
                start, wantedEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                owner
        ));
        if (hit.getType() != HitResult.Type.MISS) return hit.getPos();
        return wantedEnd;
    }

    private static void spawnSonicBeam(ServerWorld sw, Vec3d start, Vec3d end) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.01) return;

        Vec3d step = dir.normalize().multiply(PARTICLE_STEP);
        int steps = (int)Math.ceil(len / PARTICLE_STEP);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            sw.spawnParticles(ParticleTypes.SONIC_BOOM,
                    p.x, p.y, p.z,
                    1, 0, 0, 0, 0.0);
            p = p.add(step);
        }
    }

    private static void damageAlongSegment(ServerWorld sw, LivingEntity owner, Vec3d start, Vec3d end) {
        // セグメントを覆うAABB
        Box box = new Box(start, end).expand(RADIUS);

        List<LivingEntity> candidates = sw.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != owner
        );

        DamageSource src = sw.getDamageSources().magic();

        for (LivingEntity t : candidates) {
            // 太ビーム判定：点と線分の距離
            Vec3d center = t.getPos().add(0, t.getHeight() * 0.5, 0);
            double distSq = distanceSqPointToSegment(center, start, end);
            if (distSq > RADIUS * RADIUS) continue;

            // 遮蔽（ざっくり）
            if (!hasLine(sw, owner, start, center)) continue;

            // 多段が通るよう regen を潰す（必要なら）
            t.timeUntilRegen = 0;

            Vec3d beforeVel = t.getVelocity();
            boolean ok = t.damage(src, DAMAGE_PER_HIT);
            if (ok) {
                // ノックバック抑制
                t.setVelocity(beforeVel);
                t.velocityDirty = true;
            }
        }
    }

    private static boolean hasLine(ServerWorld sw, LivingEntity owner, Vec3d from, Vec3d to) {
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
        if (abLenSq < 1e-9) return p.squaredDistanceTo(a);

        double t = p.subtract(a).dotProduct(ab) / abLenSq;
        t = MathHelper.clamp(t, 0.0, 1.0);
        Vec3d proj = a.add(ab.multiply(t));
        return p.squaredDistanceTo(proj);
    }

    private static LivingEntity getLiving(ServerWorld sw, UUID id) {
        if (id == null) return null;
        Entity e = sw.getEntity(id);
        return (e instanceof LivingEntity le) ? le : null;
    }

    // ------------------------
    // NBT
    // ------------------------

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");
        if (nbt.contains("Side")) {
            try { side = Side.valueOf(nbt.getString("Side")); } catch (Exception ignored) {}
        }
        ageTicks = nbt.getInt("AgeTicks");
        dataTracker.set(LIFE, nbt.getInt("Life"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        nbt.putString("Side", side.name());
        nbt.putInt("AgeTicks", ageTicks);
        nbt.putInt("Life", dataTracker.get(LIFE));
    }
}