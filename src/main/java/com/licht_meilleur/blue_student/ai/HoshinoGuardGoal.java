package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.HoshinoEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HoshinoGuardGoal extends Goal {

    private final HoshinoEntity mob;
    private final IStudentEntity student;

    private int keepTicks = 0;

    public HoshinoGuardGoal(HoshinoEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return mob.isGuarding() && student.hasQueuedFire();
    }

    @Override
    public void start() {
        mob.setGuardShooting(true);
        keepTicks = 8; // 0.4秒くらい guard_shot を見せる
    }

    @Override
    public boolean shouldContinue() {
        return mob.isGuarding() && keepTicks > 0;
    }

    @Override
    public void tick() {
        keepTicks--;
    }

    @Override
    public void stop() {
        mob.setGuardShooting(false);
    }
}
