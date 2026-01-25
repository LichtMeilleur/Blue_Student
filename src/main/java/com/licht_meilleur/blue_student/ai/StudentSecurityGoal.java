package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class StudentSecurityGoal extends Goal {
    private final ShirokoEntity student;
    private final double speed;

    public StudentSecurityGoal(ShirokoEntity student, double speed) {
        this.student = student;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return student.getAiMode() == 1; // 1=security
    }

    @Override
    public boolean shouldContinue() {
        return student.getAiMode() == 1;
    }

    @Override
    public void tick() {
        // 「警備地点」へ戻る（未設定なら現在地を基準に固定）
        BlockPos p = student.getSecurityPos();
        if (p == null) {
            student.setSecurityPos(student.getBlockPos());
            return;
        }

        double dist2 = student.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        if (dist2 > 3.0 * 3.0) {
            student.getNavigation().startMovingTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
        } else {
            student.getNavigation().stop();
        }
    }
}