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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;

public class ShotgunHitscanWeaponAction implements WeaponAction {

    @Override
    public boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec) {
        if (!(shooter instanceof Entity shooterEntity)) return false;
        if (!(shooterEntity.getWorld() instanceof ServerWorld sw)) return false;
        if (target == null || !target.isAlive()) return false;


        // ★ここを追加：キサキ支援倍率などを反映した最終ダメージ
        float damage = spec.damage;
        if (shooterEntity instanceof AbstractStudentEntity se && se.hasKisakiSupportBuff()) {
            damage *= 1.25f; // 好きな倍率に
        }



        final Vec3d start = (shooterEntity instanceof AbstractStudentEntity se)
                ? se.getMuzzlePosApprox()
                : shooterEntity.getEyePos();

        Random r = sw.getRandom();

        int pellets = Math.max(1, spec.pellets);
        Vec3d[] dirs = new Vec3d[pellets];

        // “短射程っぽい”演出：遠いほど散りUP（命中数が減る）
        double dist = target.getEyePos().distanceTo(start);
        float spread = spec.spreadRad * (float)(1.0 + dist / 6.0);

        Vec3d baseAim = target.getEyePos().subtract(start).normalize();

        int hitCount = 0;

        // travelDist は「代表として baseAim の壁ヒット距離」を送る（描画用）
        float travelDist = computeTravelDistToBlock(sw, shooterEntity, start, baseAim, (float) spec.range);

        for (int i = 0; i < pellets; i++) {
            Vec3d dir = applySpread(baseAim, spread, r);
            dirs[i] = dir;

            Vec3d end = start.add(dir.multiply(spec.range));

            // ブロックで止める（各ペレット）
            HitResult bh = sw.raycast(new RaycastContext(
                    start, end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    shooterEntity
            ));
            if (bh.getType() != HitResult.Type.MISS) {
                end = bh.getPos();
            }

            // エンティティ判定（start→end）
            EntityHitResult ehr = raycastLiving(sw, shooterEntity, start, end);
            if (ehr != null && ehr.getEntity() == target) {
                hitCount++;
            }
        }

        // ★ここが「1回だけダメ」
        if (hitCount > 0) {
            DamageSource ds = sw.getDamageSources().mobAttack((LivingEntity) shooterEntity);

            float total = damage * hitCount;

            target.damage(ds, total);

            // ノックバック
            if (spec.knockback > 0.001f) {
                Vec3d push = baseAim;
                target.addVelocity(
                        push.x * 0.18 * spec.knockback,
                        0.05 * spec.knockback,
                        push.z * 0.18 * spec.knockback
                );
                target.velocityModified = true;
            }
        }

        // FX：散弾として複数dirを送る
        ServerFx.sendShotFx(
                sw,
                shooterEntity.getId(),
                start,
                spec.fxType,     // SHOTGUN
                spec.fxWidth,    // 使ってもいいし0でもOK
                dirs,            // ★ dirs を送る
                travelDist
        );

        return true;
    }

    private float computeTravelDistToBlock(ServerWorld sw, Entity shooter, Vec3d start, Vec3d dir, float maxRange) {
        Vec3d end = start.add(dir.normalize().multiply(maxRange));
        HitResult hit = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                shooter
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            return (float) start.distanceTo(hit.getPos());
        }
        return maxRange;
    }

    private EntityHitResult raycastLiving(ServerWorld sw, Entity shooter, Vec3d start, Vec3d end) {
        Box box = new Box(start, end).expand(0.6);
        return net.minecraft.entity.projectile.ProjectileUtil.getEntityCollision(
                sw, shooter, start, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != shooter
        );
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
