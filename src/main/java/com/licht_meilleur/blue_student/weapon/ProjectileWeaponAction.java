package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.projectile.StudentBulletEntity;
import com.licht_meilleur.blue_student.network.ServerFx;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class ProjectileWeaponAction implements WeaponAction {

    @Override
    public boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec) {
        if (!(shooter instanceof Entity shooterEntity)) return false;
        if (!(shooterEntity.getWorld() instanceof ServerWorld sw)) return false;
        if (target == null || !target.isAlive()) return false;

        // 発射位置
        final Vec3d spawnPos = (shooterEntity instanceof AbstractStudentEntity se)
                ? se.getMuzzlePosApprox()
                : shooterEntity.getEyePos();

        Random r = sw.getRandom();

        int pellets = Math.max(1, spec.pellets);
        Vec3d[] fxDirs = new Vec3d[pellets];

        Vec3d baseAim = target.getEyePos().subtract(spawnPos).normalize();

        for (int i = 0; i < pellets; i++) {
            Vec3d dir = applySpread(baseAim, spec.spreadRad, r);
            fxDirs[i] = dir; // ★FX用に保存

            StudentBulletEntity bullet = new StudentBulletEntity(sw, shooterEntity, spec.damage)
                    .setBypassIFrames(spec.bypassIFrames)
                    .setKnockback(spec.knockback);

            bullet.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            bullet.setYaw(shooterEntity.getYaw());
            bullet.setPitch(shooterEntity.getPitch());
            bullet.setVelocity(dir.x, dir.y, dir.z, spec.projectileSpeed, 0.0f);

            sw.spawnEntity(bullet);
        }

        // travelDist は「見た目の長さ」なので、とりあえず spec.range でOK（プロジェクタイルは実際に飛ぶ）
        float travelDist = (float) spec.range;

        ServerFx.sendShotFx(
                sw,
                shooterEntity.getId(),
                spawnPos,
                spec.fxType,   // BULLET or SHOTGUN
                spec.fxWidth,
                fxDirs,
                travelDist
        );

        return true;
    }

    private Vec3d applySpread(Vec3d dir, float spreadRad, Random r) {
        if (spreadRad <= 0.0001f) return dir;

        double yaw = (r.nextDouble() * 2.0 - 1.0) * spreadRad;
        double pitch = (r.nextDouble() * 2.0 - 1.0) * spreadRad;

        Vec3d d = dir;

        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        d = new Vec3d(d.x * cosY - d.z * sinY, d.y, d.x * sinY + d.z * cosY);

        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        d = new Vec3d(d.x, d.y * cosP - d.z * sinP, d.y * sinP + d.z * cosP);

        return d.normalize();
    }
}
