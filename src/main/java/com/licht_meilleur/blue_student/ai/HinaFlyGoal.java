package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.HinaEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HinaFlyGoal extends Goal {
    private final HinaEntity mob;
    private final IStudentEntity student;

    public HinaFlyGoal(HinaEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
    }

    @Override
    public boolean canStart() {
        return mob.isFlying() && student.hasQueuedFire();
    }

    @Override
    public void start() {
        mob.setFlyShooting(true);
    }

    @Override
    public void stop() {
        mob.setFlyShooting(false);
    }

    @Override
    public void tick() {
        // ここで撃たない。既存Aim/Combatが撃つ。
        // ただ「撃つ予定がある間だけ fly_shot にする」ため、キューが空なら戻す
        if (!student.hasQueuedFire()) {
            mob.setFlyShooting(false);
        }
    }
}
