package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class StudentFollowGoal extends Goal {
    private final ShirokoEntity student;
    private final double speed;
    private PlayerEntity owner;

    public StudentFollowGoal(ShirokoEntity student, double speed) {
        this.student = student;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (student.getAiMode() != 0) return false; // 0=follow
        owner = student.getOwnerPlayer();
        return owner != null && owner.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        if (student.getAiMode() != 0) return false;
        return owner != null && owner.isAlive() && student.squaredDistanceTo(owner) > 2.0 * 2.0;
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // 近いなら止まる
        double dist2 = student.squaredDistanceTo(owner);
        if (dist2 < 2.0 * 2.0) {
            student.getNavigation().stop();
            return;
        }

        // 追従（少し後ろを目標に）
        Vec3d back = owner.getPos().add(owner.getRotationVec(1.0f).multiply(-1.0));
        student.getNavigation().startMovingTo(back.x, back.y, back.z, speed);
        student.getLookControl().lookAt(owner, 30.0f, 30.0f);
    }
}