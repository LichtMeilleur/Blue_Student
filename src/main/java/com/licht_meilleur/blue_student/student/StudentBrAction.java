package com.licht_meilleur.blue_student.student;

public enum StudentBrAction {
    NONE(IStudentEntity.ShotKind.NONE, 0, 0),
    IDLE(IStudentEntity.ShotKind.NONE, 0, 0),
    //＝＝＝＝＝ホシノ＝＝＝＝＝＝＝

    MAIN_SHOT(IStudentEntity.ShotKind.MAIN, 4, 8),

    // バックステップしながらショットガン（4tick想定）
    DODGE_SHOT(IStudentEntity.ShotKind.MAIN, 4, 8),

    // 盾行動（発射なし）
    GUARD_TACKLE(IStudentEntity.ShotKind.NONE, 0, 10),
    GUARD_BASH(IStudentEntity.ShotKind.NONE, 0, 10),

    // リロードしながらサブ牽制（2tick想定）
    SUB_RELOAD_SHOT(IStudentEntity.ShotKind.SUB, 0, 4),

    // 静止サブ（2tick想定）
    SUB_SHOT(IStudentEntity.ShotKind.SUB, 0, 4),

    // 側面移動サブ（2tick想定）
    RIGHT_SIDE_SUB_SHOT(IStudentEntity.ShotKind.SUB, 0, 4),
    LEFT_SIDE_SUB_SHOT(IStudentEntity.ShotKind.SUB, 0, 4);

    public final IStudentEntity.ShotKind shotKind;
    public final int fireIntervalTicks;   // このAction中の“発射間隔”
    public final int defaultHoldTicks;    // 迷ったらこれ

    StudentBrAction(IStudentEntity.ShotKind kind, int interval, int holdTicks) {
        this.shotKind = kind;
        this.fireIntervalTicks = interval;
        this.defaultHoldTicks = holdTicks;
    }

}