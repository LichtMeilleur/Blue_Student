package com.licht_meilleur.blue_student.student;

public enum StudentLifeState {
    NORMAL,
    SLEEPING,
    RECOVERING,
    EXITING,        // exitアニメ中（ロープ上昇など）
    RESPAWN_DELAY,  // “居ない時間” (判定OFF)
    WARPING_TO_BED,
}