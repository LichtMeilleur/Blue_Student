package com.licht_meilleur.blue_student.skill;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.StudentId;

public class SkillRegistry {

    // 未実装の間は常に null を返す
    public static StudentSkill safeForStudent(StudentId id) {
        return null;
    }

    public interface StudentSkill {
        int durationTicks();
        int cooldownTicks();
        default void onStart(AbstractStudentEntity e) {}
        default void onTick(AbstractStudentEntity e) {}
        default void onEnd(AbstractStudentEntity e) {}
    }
}
