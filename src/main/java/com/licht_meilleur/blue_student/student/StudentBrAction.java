package com.licht_meilleur.blue_student.student;

public enum StudentBrAction {
    NONE(ShotKind.NONE, 0, 0),
    //＝＝＝＝＝ホシノ＝＝＝＝＝＝＝

    MAIN_SHOT(ShotKind.MAIN, 4, 8),

    // バックステップしながらショットガン（4tick想定）
    DODGE_SHOT(ShotKind.MAIN, 4, 8),

    // 盾行動（発射なし）
    GUARD_TACKLE(ShotKind.NONE, 0, 10),
    GUARD_BASH(ShotKind.NONE, 0, 10),

    // リロードしながらサブ牽制（2tick想定）
    SUB_RELOAD_SHOT(ShotKind.SUB, 2, 6),

    // 静止サブ（2tick想定）
    SUB_SHOT(ShotKind.SUB, 2, 6),

    // 側面移動サブ（2tick想定）
    RIGHT_SIDE_SUB_SHOT(ShotKind.SUB, 2, 6),
    LEFT_SIDE_SUB_SHOT(ShotKind.SUB, 2, 6);

    public final ShotKind shotKind;
    public final int fireIntervalTicks;   // このAction中の“発射間隔”
    public final int defaultHoldTicks;    // 迷ったらこれ

    StudentBrAction(ShotKind kind, int interval, int holdTicks) {
        this.shotKind = kind;
        this.fireIntervalTicks = interval;
        this.defaultHoldTicks = holdTicks;
    }

    public enum ShotKind { NONE, MAIN, SUB }
}