package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.projectile.StudentBulletEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import com.licht_meilleur.blue_student.network.ServerFx;


public class ProjectileWeaponAction implements WeaponAction {

    @Override
    public boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec) {
        if (!(shooter instanceof Entity shooterEntity)) return false;
        if (!(shooterEntity.getWorld() instanceof ServerWorld sw)) return false;
        if (target == null || !target.isAlive()) return false;

        // ===== 発射位置（サーバーではボーン取得できないので近似）=====
        final Vec3d spawnPos;
        if (shooterEntity instanceof AbstractStudentEntity se) {
            spawnPos = se.getMuzzlePosApprox(); // あなたの共通Entityに用意しておく
        } else {
            spawnPos = shooterEntity.getEyePos();
        }

        Random r = sw.getRandom();

        int pellets = Math.max(1, spec.pellets);
        for (int i = 0; i < pellets; i++) {
            // 目標へ向ける（散弾はここからブレる）
            Vec3d aim = target.getEyePos().subtract(spawnPos).normalize();
            Vec3d dir = applySpread(aim, spec.spreadRad, r);

            StudentBulletEntity bullet = new StudentBulletEntity(sw, shooterEntity, spec.damage)
                    .setBypassIFrames(spec.bypassIFrames)
                    .setKnockback(spec.knockback);

            // ★赤線回避：refreshPositionAndAngles を使わず setPosition + setVelocity で統一
            bullet.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            bullet.setYaw(shooterEntity.getYaw());
            bullet.setPitch(shooterEntity.getPitch());
            bullet.setVelocity(dir.x, dir.y, dir.z, spec.projectileSpeed, 0.0f);

            sw.spawnEntity(bullet);
        }
        Vec3d dirForFx = target.getEyePos().subtract(spawnPos).normalize();
        ServerFx.sendShotFx(sw, shooterEntity.getId(),spawnPos, dirForFx);

        return true;
    }

    private Vec3d applySpread(Vec3d dir, float spreadRad, Random r) {
        if (spreadRad <= 0.0001f) return dir;

        // -spread..+spread
        double yaw = (r.nextDouble() * 2.0 - 1.0) * spreadRad;
        double pitch = (r.nextDouble() * 2.0 - 1.0) * spreadRad;

        Vec3d d = dir;

        // yaw: Y軸回転
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        d = new Vec3d(d.x * cosY - d.z * sinY, d.y, d.x * sinY + d.z * cosY);

        // pitch: X軸回転（簡易）
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        d = new Vec3d(d.x, d.y * cosP - d.z * sinP, d.y * sinP + d.z * cosP);

        return d.normalize();
    }
}
