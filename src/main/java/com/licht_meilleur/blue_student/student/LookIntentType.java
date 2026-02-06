package com.licht_meilleur.blue_student.student;

public enum LookIntentType {
    NONE,
    TARGET,      // 指定ターゲットを見る
    MOVE_DIR,    // 移動方向を見る
    AWAY_FROM,   // 指定ターゲットから離れる方向を見る（回避用）
    WORLD_DIR,
    POS// 任意の方向ベクトルを見る（脱出など）
    //OWNER        // オーナーを見る（任意）
}
