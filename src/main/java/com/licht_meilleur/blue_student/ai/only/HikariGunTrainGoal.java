package com.licht_meilleur.blue_student.ai.only;

import com.licht_meilleur.blue_student.entity.*;
import com.licht_meilleur.blue_student.entity.go_go_train.GoGoTrainEntity;
import com.licht_meilleur.blue_student.registry.ModEntities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.UUID;

public class HikariGunTrainGoal extends Goal {

    private final HikariEntity hikari;

    private static final int CHECK_INTERVAL = 10;
    private static final double RANGE = 18.0;
    private static final double FIND_RANGE = 96.0;

    private int next = 0;

    private GunTrainEntity gun = null;

    public HikariGunTrainGoal(HikariEntity hikari) {
        this.hikari = hikari;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (hikari.getWorld() instanceof ServerWorld sw) {
            UUID ownerP = hikari.getOwnerUuid();
            if (ownerP != null) {
                // 合体列車(GoGoTrain)が居たら単体GunTrainは出さない
                if (!sw.getEntitiesByClass(GoGoTrainEntity.class,
                        hikari.getBoundingBox().expand(96),
                        e -> e.isAlive() && ownerP.equals(e.getOwnerPlayerUuid())
                ).isEmpty()) return false;
            }
        }
        return !hikari.getWorld().isClient
                && !hikari.isLifeLockedForGoal()
                && hikari.canUseGunTrainSkill();
    }

    @Override
    public boolean shouldContinue() {
        if (hikari.getWorld().isClient) return false;
        if (hikari.isLifeLockedForGoal()) return false;

        if (!hikari.canUseGunTrainSkill()) return false;

        if (hikari.getWorld() instanceof ServerWorld sw) {
            UUID ownerP = hikari.getOwnerUuid();
            if (ownerP != null && existsNozomi(sw, ownerP)) return false;
        }
        return true;
    }

    @Override
    public void stop() {
        hikari.setGunTrainSkillActive(false);
        if (hikari.hasVehicle()) hikari.stopRiding();
    }

    @Override
    public void tick() {
        if (!(hikari.getWorld() instanceof ServerWorld sw)) return;
        if (--next > 0) return;
        next = CHECK_INTERVAL;

        UUID ownerP = hikari.getOwnerUuid();
        if (ownerP == null) return;

        // ★相方(ノゾミ)が同ownerで同ワールドにいたら「単体は発動しない」
        if (existsNozomi(sw, ownerP)) {
            discardGunOnly();
            hikari.setGunTrainSkillActive(false);
            return;
        }

        // 敵チェック（敵がいないなら単体スキルOFF）
        LivingEntity target = findNearestHostile(sw, hikari.getPos(), RANGE);
        if (target == null) {
            discardGunOnly();
            hikari.setGunTrainSkillActive(false);
            return;
        }

        // GunTrain 確保（ownerPで1つだけ）
        if (gun == null || !gun.isAlive()) {
            gun = findGun(sw, ownerP);
            if (gun == null) {
                gun = new GunTrainEntity(ModEntities.GUN_TRAIN, sw)
                        .setOwnerPlayerUuid(ownerP)
                        .setPassengerStudentUuid(hikari.getUuid())
                        .setMergedMode(false);
                Vec3d spawn = computeGunSpawnPos(sw, hikari);
                gun.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, hikari.getYaw(), 0.0f);
                sw.spawnEntity(gun);
                gun.setAnchorPos(gun.getPos()); // アンカーは実際のスポーン後の座標で

                // ★ここで1回だけ固定地点を決める（乗員座標ではなく）
                gun.setAnchorPos(gun.getPos());
            }
        }


        // Hikari を座席へ
        if (hikari.getVehicle() != gun) {
            hikari.stopRiding();
            hikari.startRiding(gun, true);
        }

        hikari.setGunTrainSkillActive(true);
    }

    private void discardGunOnly() {
        if (gun != null) {
            gun.discard();
            gun = null;
            hikari.startGunTrainCooldown(); // ★ここでクール開始
        }
    }

    private GunTrainEntity findGun(ServerWorld sw, UUID ownerP) {
        Box box = hikari.getBoundingBox().expand(FIND_RANGE);
        for (GunTrainEntity e : sw.getEntitiesByClass(GunTrainEntity.class, box, x -> x.isAlive())) {
            if (ownerP.equals(e.getOwnerPlayerUuid())) return e;
        }
        return null;
    }

    private boolean existsNozomi(ServerWorld sw, UUID ownerP) {
        Box box = hikari.getBoundingBox().expand(FIND_RANGE);
        for (NozomiEntity n : sw.getEntitiesByClass(NozomiEntity.class, box, x -> x.isAlive())) {
            if (ownerP.equals(n.getOwnerUuid())) return true;
        }
        return false;
    }

    private LivingEntity findNearestHostile(ServerWorld sw, Vec3d center, double range) {
        Box box = new Box(center, center).expand(range, 6.0, range);
        HostileEntity best = null;
        double bestD2 = 1e18;

        for (HostileEntity e : sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
            double d2 = e.squaredDistanceTo(center);
            if (d2 < bestD2) { bestD2 = d2; best = e; }
        }
        return best;
    }
    private Vec3d computeGunSpawnPos(ServerWorld sw, HikariEntity h) {
        // 前方に 1.2、上に 0.2（好みで調整）
        Vec3d forward = forwardFromYaw(h.getYaw()).normalize();
        Vec3d base = h.getPos().add(0, 0.2, 0);

        // 候補：前→前+右→前+左→その場→後ろ（詰まり回避）
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        Vec3d[] candidates = new Vec3d[] {
                base.add(forward.multiply(1.2)),
                base.add(forward.multiply(1.0)).add(right.multiply(0.9)),
                base.add(forward.multiply(1.0)).add(right.multiply(-0.9)),
                base,
                base.add(forward.multiply(-0.8))
        };

        for (Vec3d p : candidates) {
            Vec3d grounded = snapToGround(sw, p);
            if (isFree(sw, grounded)) return grounded;
        }

        // どれもダメなら最後は無理やり足元（noClipならほぼ問題にならない）
        return snapToGround(sw, base);
    }

    private Vec3d snapToGround(ServerWorld sw, Vec3d p) {
        int x = net.minecraft.util.math.MathHelper.floor(p.x);
        int z = net.minecraft.util.math.MathHelper.floor(p.z);

        int yTop = sw.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        // GunTrainは少し浮かせる（地面めり込み防止）
        return new Vec3d(p.x, yTop + 0.5, p.z);
    }

    private boolean isFree(ServerWorld sw, Vec3d p) {
        // GunTrainの当たり判定が小さいなら 0.8x0.8 くらいで十分
        Box box = new Box(p.x - 0.45, p.y, p.z - 0.45, p.x + 0.45, p.y + 1.2, p.z + 0.45);
        return sw.isSpaceEmpty(null, box);
    }

    private static Vec3d forwardFromYaw(float yawDeg) {
        float r = yawDeg * net.minecraft.util.math.MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-net.minecraft.util.math.MathHelper.sin(r), 0, net.minecraft.util.math.MathHelper.cos(r));
    }

}