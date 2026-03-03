package com.licht_meilleur.blue_student.ai.only;

import com.licht_meilleur.blue_student.entity.*;
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
        return !hikari.getWorld().isClient && !hikari.isLifeLockedForGoal();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
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
                        .setPassengerStudentUuid(hikari.getUuid());

                gun.setPosition(hikari.getX(), hikari.getY(), hikari.getZ());
                sw.spawnEntity(gun);

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
        if (gun != null) { gun.discard(); gun = null; }
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
}