package com.licht_meilleur.blue_student.ai.only;

import com.licht_meilleur.blue_student.entity.HikariEntity;
import com.licht_meilleur.blue_student.entity.NozomiEntity;
import com.licht_meilleur.blue_student.entity.go_go_train.GoGoGunTrainEntity;
import com.licht_meilleur.blue_student.entity.go_go_train.GoGoTrainEntity;
import com.licht_meilleur.blue_student.registry.ModEntities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.UUID;

public class NozomiHikariMergeGoGoTrainGoal extends Goal {

    private final NozomiEntity nozomi;

    private static final int CHECK_INTERVAL = 10;
    private static final double RANGE = 18.0;
    private static final double FIND_RANGE = 96.0;

    private int next = 0;

    private GoGoTrainEntity gogo;
    private GoGoGunTrainEntity gogoGun;


    public NozomiHikariMergeGoGoTrainGoal(NozomiEntity nozomi) {
        this.nozomi = nozomi;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (!(nozomi.getWorld() instanceof ServerWorld sw)) return false;
        if (nozomi.isLifeLockedForGoal()) return false;
        if (!nozomi.canUseUnisonSkill()) return false;
        UUID owner = nozomi.getOwnerUuid();
        if (owner == null) return false;

        // 相方（同オーナー）が近くにいる
        HikariEntity hikari = findHikari(sw, owner);
        if (hikari == null || !hikari.isAlive()) return false;

        // ★敵がいる時だけ発動（敵なし発動を止める）
        LivingEntity target = findNearestHostile(sw, nozomi.getPos(), RANGE);

        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!(nozomi.getWorld() instanceof ServerWorld sw)) return false;
        if (nozomi.isLifeLockedForGoal()) return false;

        UUID owner = nozomi.getOwnerUuid();
        if (owner == null) return false;

        // 相方が居なければ終了
        HikariEntity hikari = findHikari(sw, owner);
        if (hikari == null || !hikari.isAlive()) return false;

        // ★敵がいないなら解除（維持したいならここを true に変える）
        LivingEntity target = findNearestHostile(sw, nozomi.getPos(), RANGE);
        if (target == null) return false;

        nozomi.startUnisonCooldown();
        // gogoが生きてるなら続行
        return gogo != null && gogo.isAlive();

    }

    @Override
    public void stop() {
        if (!(nozomi.getWorld() instanceof ServerWorld sw)) return;

        UUID owner = nozomi.getOwnerUuid();
        HikariEntity hikari = (owner != null) ? findHikari(sw, owner) : null;

        // フラグOFF
        nozomi.setTrainSkillActive(false);
        if (hikari != null) hikari.setGunTrainSkillActive(false);

        // 生成物破棄
        discardMergedEntitiesIfAny();

        // 騎乗解除（ノゾミのみ）
        if (nozomi.hasVehicle()) nozomi.stopRiding();

        // 間引きカウンタも戻す
        next = 0;

    }

    @Override
    public void tick() {
        if (!(nozomi.getWorld() instanceof ServerWorld sw)) return;



        // ★間引き（安定化）
        if (--next > 0) return;
        next = CHECK_INTERVAL;

        UUID ownerP = nozomi.getOwnerUuid();
        if (ownerP == null) return;

        // 0) 相方確認
        HikariEntity hikari = findHikari(sw, ownerP);
        if (hikari == null || !hikari.isAlive()) {
            // 解除
            nozomi.setTrainSkillActive(false);
            discardMergedEntitiesIfAny();
            return;
        }

        // 1) 敵確認（敵なしなら解除）
        LivingEntity target = findNearestHostile(sw, nozomi.getPos(), RANGE);
        if (target == null) {
            hikari.setGunTrainSkillActive(false);
            nozomi.setTrainSkillActive(false);
            discardMergedEntitiesIfAny();
            return;
        }

        // 敵がいる → 合体中フラグON（アニメ用）
        hikari.setGunTrainSkillActive(true);
        nozomi.setTrainSkillActive(true);

        // 2) GoGoTrain 確保
        if (gogo == null || !gogo.isAlive()) {
            gogo = findGoGoTrain(sw, ownerP);
        }
        if (gogo == null) {
            Vec3d spawn = computeTrainSpawnPos(nozomi);

            gogo = new GoGoTrainEntity(ModEntities.GO_GO_TRAIN, sw)
                    .setOwnerPlayerUuid(ownerP)
                    .setNozomiPassengerUuid(nozomi.getUuid())
                    .setHikariPassengerUuid(hikari.getUuid())
                    .setClockwise(true);

            gogo.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, nozomi.getYaw(), 0.0f);
            sw.spawnEntity(gogo);
        }

        // 3) GoGoGunTrain 確保（後方車両）
        if (gogoGun == null || !gogoGun.isAlive()) {
            gogoGun = findGoGoGunTrain(sw, ownerP);
        }
        if (gogoGun == null) {
            Vec3d gunSpawn = computeGunSpawnBehindTrain(gogo);

            gogoGun = new GoGoGunTrainEntity(ModEntities.GO_GO_GUN_TRAIN, sw)
                    .setOwnerPlayerUuid(ownerP)
                    .setTrainUuid(gogo.getUuid())
                    .setPassengerStudentUuid(hikari.getUuid())
                    .setMergedMode(true);

            float yaw = gogo.getYaw(); // 必要なら +90/-90
            gogoGun.refreshPositionAndAngles(gunSpawn.x, gunSpawn.y, gunSpawn.z, yaw, 0.0f);
            sw.spawnEntity(gogoGun);
        }

        // 4) ターゲット同期
        gogo.setTargetUuid(target.getUuid());



        // 5) 後方車両を先頭に追従（遅れ軽減）
        followGunToTrain(gogo, gogoGun);

        // 6) ノゾミは先頭に騎乗
        if (nozomi.getVehicle() != gogo) {
            nozomi.stopRiding();
            nozomi.startRiding(gogo, true);
        }

        // 7) ヒカリは後方車両に騎乗
        if (hikari.getVehicle() != gogoGun) {
            hikari.stopRiding();
            hikari.startRiding(gogoGun, true);
        }

    }

    /* -------------------------
       補助関数
       ------------------------- */



    private Vec3d computeTrainSpawnPos(NozomiEntity noz) {
        return noz.getPos().add(0, 0.2, 0);
    }

    private Vec3d computeGunSpawnBehindTrain(GoGoTrainEntity train) {
        float yaw = train.getYaw();
        Vec3d forward = forwardFromYaw(yaw).normalize();
        Vec3d base = train.getPos();

        double back = 2.2; // 調整
        return base.add(forward.multiply(-back));
    }

    private void followGunToTrain(GoGoTrainEntity train, GoGoGunTrainEntity gun) {
        if (train == null || gun == null) return;
        if (!train.isAlive() || !gun.isAlive()) return;

        float yaw = train.getYaw();
        Vec3d forward = forwardFromYaw(yaw).normalize();
        Vec3d base = train.getPos();

        double back = 2.2;   // 調整
        double right = 0.0;  // 調整
        double up = 0.0;     // 調整

        Vec3d rightV = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d pos = base.add(0, up, 0)
                .add(forward.multiply(-back))
                .add(rightV.multiply(right));

        gun.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0f);
        gun.setVelocity(Vec3d.ZERO);
        gun.velocityModified = true;
    }

    private LivingEntity findNearestHostile(ServerWorld sw, Vec3d center, double range) {
        Box box = new Box(center, center).expand(range, 6.0, range);

        HostileEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (HostileEntity e : sw.getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive)) {
            double d2 = e.squaredDistanceTo(center);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }

    private static Vec3d forwardFromYaw(float yawDeg) {
        float r = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(r), 0, MathHelper.cos(r));
    }

    private HikariEntity findHikari(ServerWorld sw, UUID ownerP) {
        Box box = nozomi.getBoundingBox().expand(FIND_RANGE);
        for (HikariEntity h : sw.getEntitiesByClass(HikariEntity.class, box, e -> e.isAlive())) {
            UUID ho = h.getOwnerUuid();
            if (ho != null && ho.equals(ownerP)) return h;
        }
        return null;
    }

    private GoGoTrainEntity findGoGoTrain(ServerWorld sw, UUID ownerP) {
        Box box = nozomi.getBoundingBox().expand(FIND_RANGE);
        for (GoGoTrainEntity e : sw.getEntitiesByClass(GoGoTrainEntity.class, box, x -> x.isAlive())) {
            UUID o = e.getOwnerPlayerUuid();
            if (o != null && o.equals(ownerP)) return e;
        }
        return null;
    }

    private GoGoGunTrainEntity findGoGoGunTrain(ServerWorld sw, UUID ownerP) {
        Box box = nozomi.getBoundingBox().expand(FIND_RANGE);
        for (GoGoGunTrainEntity e : sw.getEntitiesByClass(GoGoGunTrainEntity.class, box, x -> x.isAlive())) {
            UUID o = e.getOwnerPlayerUuid();
            if (o != null && o.equals(ownerP)) return e;
        }
        return null;
    }

    private void discardMergedEntitiesIfAny() {
        if (gogoGun != null) {
            gogoGun.discard();
            gogoGun = null;
        }
        if (gogo != null) {
            gogo.discard();
            gogo = null;
        }
    }
}