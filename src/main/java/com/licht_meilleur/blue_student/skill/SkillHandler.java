package com.licht_meilleur.blue_student.skill;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;

public interface SkillHandler {
    String animName();          // GeckoLibのアニメ名
    int durationTicks();
    int cooldownTicks();

    default void onStart(AbstractStudentEntity se) {}
    default void onTick(AbstractStudentEntity se) {}
    default void onEnd(AbstractStudentEntity se) {}
}
