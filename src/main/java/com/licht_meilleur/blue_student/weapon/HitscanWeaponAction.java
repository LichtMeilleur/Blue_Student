package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.network.ServerFx;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class HitscanWeaponAction implements WeaponAction {

    @Override
    public boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec) {
        if (!(shooter instanceof Entity shooterEntity)) return false;
        if (!(shooterEntity.getWorld() instanceof ServerWorld sw)) return false;

        // 発射位置（サーバーは近似）
        final Vec3d start = (shooterEntity instanceof AbstractStudentEntity se)
                ? se.getMuzzlePosApprox()
                : shooterEntity.getEyePos();

        // 方向：基本は target を狙う（いなければ視線）
        Vec3d dir = (target != null && target.isAlive())
                ? target.getEyePos().subtract(start).normalize()
                : shooterEntity.getRotationVec(1.0f).normalize();

        // 最大射程
        final double maxRange = spec.range;
        Vec3d end = start.add(dir.multiply(maxRange));

        // ===== ブロックで止める（レールガン要件）=====
        HitResult blockHit = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                shooterEntity
        ));

        Vec3d hitEnd = end;
        double travelDist = maxRange;

        if (blockHit.getType() != HitResult.Type.MISS) {
            hitEnd = blockHit.getPos();
            travelDist = start.distanceTo(hitEnd);
            end = hitEnd; // ★以降の判定も同じ終点を使う
        }

        // ===== エンティティ命中判定（start->end の線分上）=====
        Entity hit = raycastLiving(sw, shooterEntity, start, end);
        if (hit instanceof LivingEntity le && le.isAlive()) {
            DamageSource ds = sw.getDamageSources().mobAttack((LivingEntity) shooterEntity);
            le.damage(ds, spec.damage);

            // ノックバック
            if (spec.knockback > 0.001f) {
                le.addVelocity(
                        dir.x * 0.2 * spec.knockback,
                        0.05 * spec.knockback,
                        dir.z * 0.2 * spec.knockback
                );
                le.velocityModified = true;
            }
        }

        // ===== FX送信（BULLET/RAILGUN どちらもここでOK）=====
        Vec3d[] fxDirs = new Vec3d[]{dir};

        ServerFx.sendShotFx(
                sw,
                shooterEntity.getId(),
                start,
                spec.fxType,
                spec.fxWidth,
                fxDirs,
                (float) travelDist
        );

        return true;
    }

    private Entity raycastLiving(ServerWorld sw, Entity shooter, Vec3d start, Vec3d end) {
        // 探索用AABB（太め）
        Box box = new Box(start, end).expand(1.0);

        EntityHitResult ehr = net.minecraft.entity.projectile.ProjectileUtil.getEntityCollision(
                sw,
                shooter,
                start,
                end,
                box,
                e -> e instanceof LivingEntity && e.isAlive() && e != shooter
        );
        return ehr != null ? ehr.getEntity() : null;
    }
}
