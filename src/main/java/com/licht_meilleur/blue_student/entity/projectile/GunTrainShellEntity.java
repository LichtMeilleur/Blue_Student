package com.licht_meilleur.blue_student.entity.projectile;

import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.UUID;

public class GunTrainShellEntity extends Entity implements FlyingItemEntity {

    private UUID ownerUuid;

    // ★追尾対象
    private UUID targetUuid;

    private int lifeTicks = 20 * 5; // 5秒で自然消滅
    private int ageTicks = 0;

    private static final float BLAST_RADIUS = 4.5f;
    private static final float DAMAGE = 8.0f;

    // ===== ミサイル挙動パラメータ =====
    private static final int CURVE_TICKS = 5;   // 最初の10tickは「外側へ膨らむ」
    private static final double STEER = 0.50;    // 追尾の曲がり具合（0.06〜0.18で調整）
    private static final double MAX_SPEED = 1.35;// 速すぎると当たり判定が抜けやすい（1.0〜1.6）
    private static final double MIN_SPEED = 0.90;// 遅すぎると追尾感が薄い
    private static final double CURVE_FORCE = 0.10; // 外側へ押す力（0.05〜0.16）
    private static final double UP_FORCE = 0.015;   // 少し上昇させる（ミサイル感）

    // 外側へ膨らむ向き（右/左）を固定するための符号
    private int curveSign = 1;

    public GunTrainShellEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(true);
    }

    public GunTrainShellEntity setOwnerUuid(UUID owner) {
        this.ownerUuid = owner;
        return this;
    }

    // ★発射時にターゲットも渡す（外側→追尾に必要）
    public GunTrainShellEntity setTarget(LivingEntity target) {
        if (target != null) this.targetUuid = target.getUuid();
        return this;
    }

    // ★右/左どっちに膨らむか指定（任意）
    public GunTrainShellEntity setCurveSign(int sign) {
        this.curveSign = (sign >= 0) ? 1 : -1;
        return this;
    }

    @Override
    public ItemStack getStack() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    protected void initDataTracker() { }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;



        ageTicks++;

        if (--lifeTicks <= 0) {
            this.discard();
            return;
        }

        // ★煙（常時）
        Vec3d cur = this.getPos();
        sw.spawnParticles(ParticleTypes.SMOKE, cur.x, cur.y, cur.z, 1, 0.02, 0.02, 0.02, 0.001);

        // ===== 誘導（外側→追尾）=====
        steerMissile(sw);
        // =====敵にアイテム描画を向ける=====
        updateRotationFromVelocity();

        // 直進（誘導後のvelで）
        Vec3d vel = this.getVelocity();
        Vec3d next = cur.add(vel);

        // 当たり判定：ブロック
        HitResult hit = this.getWorld().raycast(new RaycastContext(
                cur, next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        // エンティティヒット
        EntityHitResult eHit = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                cur,
                next,
                this.getBoundingBox().stretch(vel).expand(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != this
        );

        HitResult finalHit = hit;
        if (eHit != null && (hit.getType() == HitResult.Type.MISS ||
                eHit.getPos().squaredDistanceTo(cur) < hit.getPos().squaredDistanceTo(cur))) {
            finalHit = eHit;
        }

        // 移動（いったんヒット地点へ）
        this.setPosition(finalHit.getPos().x, finalHit.getPos().y, finalHit.getPos().z);

        if (finalHit.getType() != HitResult.Type.MISS) {
            explodeNoBlock(sw, this.getPos());
            this.discard();
            return;
        }

        // MISSなら進む
        this.setPosition(next.x, next.y, next.z);
    }

    private void steerMissile(ServerWorld sw) {
        Vec3d vel = this.getVelocity();
        if (vel.lengthSquared() < 1.0e-9) {
            // 速度ゼロなら前に出す（保険）
            vel = new Vec3d(0, 0, 1).multiply(MIN_SPEED);
        }

        // 速度の大きさを維持しつつ、向きだけを調整する
        double speed = vel.length();
        speed = MathHelper.clamp(speed, MIN_SPEED, MAX_SPEED);

        // ターゲット取得
        Entity te = (targetUuid != null) ? sw.getEntity(targetUuid) : null;
        LivingEntity target = (te instanceof LivingEntity le && le.isAlive()) ? le : null;

        // ターゲットが無いなら、軽い上昇だけして直進
        if (target == null) {
            Vec3d n = vel.normalize();
            Vec3d out = new Vec3d(n.x, n.y + UP_FORCE, n.z).normalize().multiply(speed);
            this.setVelocity(out);
            return;
        }

        // 目標方向（少し上を狙うと地面に刺さりにくい）
        Vec3d aimPos = target.getEyePos();
        Vec3d desiredDir = aimPos.subtract(this.getPos()).normalize();

        Vec3d curDir = vel.normalize();

        // 最初のCURVE_TICKSは「横に膨らむ」
        if (ageTicks <= CURVE_TICKS) {
            // 現在の進行方向に対する右ベクトル
            Vec3d right = curDir.crossProduct(new Vec3d(0, 1, 0));
            if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
            right = right.normalize().multiply(CURVE_FORCE * curveSign);

            // 少し上昇 + 横へ
            Vec3d bent = curDir.add(right).add(0, UP_FORCE, 0).normalize();
            this.setVelocity(bent.multiply(speed));
            return;
        }

        // 追尾：向きを少しずつ desired に寄せる（急旋回しない）
        Vec3d mixed = curDir.multiply(1.0 - STEER).add(desiredDir.multiply(STEER)).normalize();
        mixed = mixed.add(0, UP_FORCE, 0).normalize();

        this.setVelocity(mixed.multiply(speed));
    }

    private void explodeNoBlock(ServerWorld sw, Vec3d pos) {
        // 演出
        sw.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 18, 0.35, 0.2, 0.35, 0.02);

        // 音
        sw.playSound(null, BlockPos.ofFloored(pos),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.0f);

        // 範囲ダメ（ブロック破壊なし）
        Box box = new Box(pos, pos).expand(BLAST_RADIUS, 2.5, BLAST_RADIUS);
        for (HostileEntity h : sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
            if (h.squaredDistanceTo(pos) <= (BLAST_RADIUS * BLAST_RADIUS)) {
                h.damage(sw.getDamageSources().magic(), DAMAGE);
            }
        }
    }

    // ===== NBT =====
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");

        lifeTicks = nbt.getInt("Life");
        ageTicks = nbt.getInt("Age");
        curveSign = nbt.getInt("CurveSign");

        double vx = nbt.getDouble("Vx");
        double vy = nbt.getDouble("Vy");
        double vz = nbt.getDouble("Vz");
        this.setVelocity(vx, vy, vz);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);

        nbt.putInt("Life", lifeTicks);
        nbt.putInt("Age", ageTicks);
        nbt.putInt("CurveSign", curveSign);

        Vec3d v = this.getVelocity();
        nbt.putDouble("Vx", v.x);
        nbt.putDouble("Vy", v.y);
        nbt.putDouble("Vz", v.z);
    }
    private void updateRotationFromVelocity() {
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() < 1.0e-8) return;

        float yaw = (float)(MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        float pitch = (float)(-(MathHelper.atan2(v.y, horiz) * (180.0 / Math.PI)));

        this.setYaw(yaw);
        this.setPitch(pitch);
        this.prevYaw = yaw;
        this.prevPitch = pitch;
    }
}