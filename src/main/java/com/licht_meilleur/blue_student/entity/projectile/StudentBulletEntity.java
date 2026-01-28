package com.licht_meilleur.blue_student.entity.projectile;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class StudentBulletEntity extends ThrownItemEntity {

    private float damage = 2.0f;

    private boolean bypassIFrames = false;
    private float knockback = 0.0f;

    public StudentBulletEntity(net.minecraft.entity.EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public StudentBulletEntity(World world, Entity owner, float damage) {
        super(BlueStudentMod.STUDENT_BULLET, world);
        this.setOwner(owner);
        this.damage = damage;
        this.setNoGravity(true);
    }

    public StudentBulletEntity setBypassIFrames(boolean v) {
        this.bypassIFrames = v;
        return this;
    }

    public StudentBulletEntity setKnockback(float kb) {
        this.knockback = kb;
        return this;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    public void tick() {
        super.tick();

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onCollision(hit);
        }

        if (this.age > 80) {
            this.discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult r) {
        super.onEntityHit(r);

        Entity hit = r.getEntity();
        Entity owner = this.getOwner();
        if (hit == owner) return;

        // ダメージ
        boolean damaged = hit.damage(this.getDamageSources().thrown(this, owner), damage);

        if (damaged) {
            // 無敵時間の軽減（効く相手にだけ）
            if (bypassIFrames && hit instanceof LivingEntity le) {
                // 完全保証ではないけど、連射の体感が上がることが多い
                le.timeUntilRegen = 0;
                le.hurtTime = 0;
            }

            // ノックバック
            if (knockback > 0.001f && hit instanceof LivingEntity le) {
                Vec3d v = this.getVelocity();
                Vec3d horiz = new Vec3d(v.x, 0, v.z);
                if (horiz.lengthSquared() < 1.0e-6) {
                    // ほぼ真上/停止なら発射者方向から
                    Vec3d from = le.getPos().subtract(this.getPos());
                    horiz = new Vec3d(from.x, 0, from.z);
                }
                Vec3d dir = horiz.normalize();

                le.addVelocity(dir.x * knockback, 0.05, dir.z * knockback);
                le.velocityDirty = true;
            }
        }

        this.discard();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) this.discard();
    }
}
