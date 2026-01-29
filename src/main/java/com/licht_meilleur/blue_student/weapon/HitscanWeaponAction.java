package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.network.ServerFx;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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

        Vec3d end = start.add(dir.multiply(spec.range));

        // 簡易レイ（エンティティのみ）：ブロック判定も入れるなら Raycast を追加
        Entity hit = raycastLiving(sw, shooterEntity, start, end);
        if (hit instanceof LivingEntity le && le.isAlive()) {
            DamageSource ds = sw.getDamageSources().mobAttack((LivingEntity) shooterEntity);
            le.damage(ds, spec.damage);

            // ノックバック
            if (spec.knockback > 0.001f) {
                le.addVelocity(dir.x * 0.2 * spec.knockback, 0.05 * spec.knockback, dir.z * 0.2 * spec.knockback);
                le.velocityModified = true;
            }
        }

        // FX送信：ここは1回でOK（クライアント側で太ビーム描画）
        ServerFx.sendShotFx(sw, shooterEntity.getId(), start, dir);
        return true;
    }

    private Entity raycastLiving(ServerWorld sw, Entity shooter, Vec3d start, Vec3d end) {
        // 探索用AABB（太めに取る）
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
