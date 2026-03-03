package com.licht_meilleur.blue_student.entity.projectile;

import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.UUID;

public class GunTrainShellEntity extends Entity {

    private UUID ownerUuid;
    private int lifeTicks = 20 * 5; // 5秒で自然消滅

    private static final float BLAST_RADIUS = 4.5f;
    private static final float DAMAGE = 8.0f;

    public GunTrainShellEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(true); // 砲弾だけど今回は直進（必要ならfalseで重力）
    }

    public GunTrainShellEntity setOwnerUuid(UUID owner) {
        this.ownerUuid = owner;
        return this;
    }

    @Override
    protected void initDataTracker() { }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        if (--lifeTicks <= 0) {
            this.discard();
            return;
        }

        // 直進
        Vec3d cur = this.getPos();
        Vec3d vel = this.getVelocity();
        Vec3d next = cur.add(vel);

        // 当たり判定：ブロック
        HitResult hit = this.getWorld().raycast(new RaycastContext(
                cur, next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        // エンティティヒットも見る
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

        // 移動
        this.setPosition(finalHit.getPos().x, finalHit.getPos().y, finalHit.getPos().z);

        if (finalHit.getType() != HitResult.Type.MISS) {
            explodeNoBlock(sw, this.getPos());
            this.discard();
            return;
        }

        // ミスなら進む
        this.setPosition(next.x, next.y, next.z);
    }

    private void explodeNoBlock(ServerWorld sw, Vec3d pos) {
        // ①演出（粒子）
        sw.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 18, 0.35, 0.2, 0.35, 0.02);

        // ②音
        sw.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.0f);

        // ③範囲ダメ（ブロック破壊なし）
        Box box = new Box(pos, pos).expand(BLAST_RADIUS, 2.5, BLAST_RADIUS);
        for (HostileEntity h : sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
            if (h.squaredDistanceTo(pos) <= (BLAST_RADIUS * BLAST_RADIUS)) {
                // owner責任にしたい場合は owner を DamageSource に入れるのが理想だけど、
                // Yarnのsignature差があるので、まずはmagicで安定させる
                h.damage(sw.getDamageSources().magic(), DAMAGE);
            }
        }
    }

    // ===== NBT =====
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        lifeTicks = nbt.getInt("Life");
        double vx = nbt.getDouble("Vx");
        double vy = nbt.getDouble("Vy");
        double vz = nbt.getDouble("Vz");
        this.setVelocity(vx, vy, vz);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("Life", lifeTicks);
        Vec3d v = this.getVelocity();
        nbt.putDouble("Vx", v.x);
        nbt.putDouble("Vy", v.y);
        nbt.putDouble("Vz", v.z);
    }
}