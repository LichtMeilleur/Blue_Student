package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;

public class StudentRideWithOwnerGoal extends Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private PlayerEntity owner;
    private Entity ownerVehicle;

    public StudentRideWithOwnerGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        // FOLLOW/SECURITY中に限定したいならここで絞る
        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        owner = resolveOwner();
        if (owner == null || !owner.isAlive()) return false;

        ownerVehicle = owner.getVehicle();

        // オーナーがボート系に乗っている？
        if (!isBoatLike(ownerVehicle)) return false;

        // すでに同じ乗り物なら不要
        if (mob.getVehicle() == ownerVehicle) return false;

        // 近いときだけ乗りに行く（遠すぎる時はReturnToOwnerに任せる）
        return mob.squaredDistanceTo(owner) < (5.0 * 5.0);
    }

    @Override
    public boolean shouldContinue() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (owner == null || !owner.isAlive()) return false;

        // オーナーがボートから降りたら終了（=tickで降ろす）
        ownerVehicle = owner.getVehicle();
        if (!isBoatLike(ownerVehicle)) return false;

        // まだ同じボートに乗れてない間だけ継続
        return mob.getVehicle() != ownerVehicle;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        owner = null;
        ownerVehicle = null;
    }

    @Override
    public void tick() {
        if (owner == null || ownerVehicle == null) return;

        // 近づく（座席に乗るため）
        mob.getNavigation().startMovingTo(owner, 1.4);

        // 近距離になったら搭乗
        if (mob.squaredDistanceTo(owner) < (2.2 * 2.2)) {
            // ボートの座席が埋まってると乗れないので、その場合は追従だけにする
            if (ownerVehicle.getPassengerList().size() < 2) {
                mob.startRiding(ownerVehicle, true);
                mob.getNavigation().stop();
            }
        }
    }

    // 呼び出し元はあなたのAbstractStudentEntityの実装に合わせる
    private PlayerEntity resolveOwner() {
        if (student instanceof AbstractStudentEntity se) {
            PlayerEntity p = se.getOwnerPlayer();
            if (p != null) return p;
        }
        return mob.getWorld().getClosestPlayer(mob, 32.0);
    }

    private boolean isBoatLike(Entity e) {
        return (e instanceof BoatEntity) || (e instanceof ChestBoatEntity);
    }
}
