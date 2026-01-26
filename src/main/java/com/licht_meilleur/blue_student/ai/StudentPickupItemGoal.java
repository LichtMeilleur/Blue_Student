package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.entity.ai.goal.Goal;

public class StudentPickupItemGoal extends Goal {
    private final ShirokoEntity student;

    public StudentPickupItemGoal(ShirokoEntity student) {
        this.student = student;
    }

    @Override
    public boolean canStart() {
        return true;
    }

}