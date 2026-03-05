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

public class NozomiTrainGoal extends Goal {

    private final NozomiEntity nozomi;

    private static final int CHECK_INTERVAL = 10;
    private static final double RANGE = 18.0;
    private static final double FIND_RANGE = 96.0;

    private int next = 0;

    private TrainEntity train = null;

    private int shootCd = 0;

    public NozomiTrainGoal(NozomiEntity nozomi) {
        this.nozomi = nozomi;
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (nozomi.getWorld() instanceof ServerWorld sw) {
            UUID ownerP = nozomi.getOwnerUuid();
            if (ownerP != null) {
                // 合体列車(GoGoTrain)が居たら単体Trainは出さない
                if (!sw.getEntitiesByClass(GoGoTrainEntity.class,
                        nozomi.getBoundingBox().expand(96),
                        e -> e.isAlive() && ownerP.equals(e.getOwnerPlayerUuid())
                ).isEmpty()) return false;
            }
        }
        return !nozomi.getWorld().isClient
                && !nozomi.isLifeLockedForGoal()
                && nozomi.canUseTrainSkill();

    }

    @Override
    public boolean shouldContinue() {
        if (nozomi.getWorld().isClient) return false;
        if (nozomi.isLifeLockedForGoal()) return false;

        // クール中なら続けない（Trainが消えた後の復帰にも効く）
        if (!nozomi.canUseTrainSkill()) return false;

        // 相方が来たら単体は終了
        if (nozomi.getWorld() instanceof ServerWorld sw) {
            UUID ownerP = nozomi.getOwnerUuid();
            if (ownerP != null && existsHikari(sw, ownerP)) return false;
        }
        return true;
    }

    @Override
    public void stop() {
        nozomi.setTrainSkillActive(false);
        // 乗ってたら降ろす（任意：残留防止）
        if (nozomi.hasVehicle()) nozomi.stopRiding();
    }

    @Override
    public void tick() {
        if (!(nozomi.getWorld() instanceof ServerWorld sw)) return;
        if (--next > 0) return;
        next = CHECK_INTERVAL;

        // owner(プレイヤーUUID) が無いなら何もしない
        UUID ownerP = nozomi.getOwnerUuid();
        if (ownerP == null) return;

        // ★相方(ヒカリ)が同ownerで同ワールドにいたら「単体は発動しない」
        if (existsHikari(sw, ownerP)) {
            discardTrainOnly();
            nozomi.setTrainSkillActive(false);
            return;
        }

        // 敵チェック
        LivingEntity target = findNearestHostile(sw, nozomi.getPos(), RANGE);
        if (target == null) {
            discardTrainOnly();
            nozomi.setTrainSkillActive(false);
            return;
        }

        // Train 確保（ownerPで1つだけ）
        if (train == null || !train.isAlive()) {
            train = findTrain(sw, ownerP);
            if (train == null) {
                train = new TrainEntity(ModEntities.TRAIN, sw)
                        .setOwnerPlayerUuid(ownerP);
                train.setPosition(nozomi.getX(), nozomi.getY(), nozomi.getZ());
                sw.spawnEntity(train);
            }
        }

        train.setMode(TrainEntity.TrainMode.SINGLE_CHARGE);
        train.setGunTrainUuid(null);          // 単体なら gun を切る（残骸リンクで誤判定しない）
        train.setClockwise(true);             // 無害だけど単体では意味なし
        train.setTargetUuid(target.getUuid());
        train.setNozomiPassengerUuid(nozomi.getUuid());
        nozomi.setTrainSkillActive(true);




        // Nozomi を座席へ
        if (nozomi.getVehicle() != train) {
            nozomi.stopRiding();
            nozomi.startRiding(train, true);
        }


        // まず視線（体の向きは車体に固定するので、AimAngles用）
        nozomi.requestLookTarget(target, 80, 2);


        // アニメON
        nozomi.setTrainSkillActive(true);
    }

    private void discardTrainOnly() {
        if (train != null) { train.discard(); train = null; }
    }

    private TrainEntity findTrain(ServerWorld sw, UUID ownerP) {
        Box box = nozomi.getBoundingBox().expand(FIND_RANGE);
        for (TrainEntity e : sw.getEntitiesByClass(TrainEntity.class, box, x -> x.isAlive())) {
            if (ownerP.equals(e.getOwnerPlayerUuid())) return e;
        }
        return null;
    }

    private boolean existsHikari(ServerWorld sw, UUID ownerP) {
        Box box = nozomi.getBoundingBox().expand(FIND_RANGE);
        for (HikariEntity h : sw.getEntitiesByClass(HikariEntity.class, box, x -> x.isAlive())) {
            if (ownerP.equals(h.getOwnerUuid())) return true;
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