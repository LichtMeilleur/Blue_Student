package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.student.StudentId;

public class WeaponSpecs {

    public static WeaponSpec forStudent(StudentId id) {
        return switch (id) {

            // シロコ：遠距離 標準AR想定
            case SHIROKO -> WeaponSpec.projectile(
                    16,
                    2,
                    1.2f,
                    3.0f,
                    0.04f,
                    1,
                    0.0f,   // ノックバック無し
                    true,
                    8.0,    // preferredMinRange
                    14.0,    // preferredMaxRange
                    30,             // magSize
                    20,             // reloadTicks
                    0,              // reloadStartAmmo
                    5.0,    // panicRange
                    false
            );

            // ホシノ：近〜中距離 ショットガン想定（散弾）
            case HOSHINO -> WeaponSpec.projectile(
                    10,
                    15,
                    3.0f,
                    2.0f,
                    0.25f,
                    8,
                    0.8f,   // 強ノックバック
                    true,
                    3.0,    // preferredMinRange
                    7.0,    // preferredMaxRange
                    5,             // magSize
                    43,             // reloadTicks
                    0,              // reloadStartAmmo
                    4.0,            // panicRange
                    false           // infiniteAmmo
            );

            // ヒナ：遠距離 高レート
            case HINA -> WeaponSpec.projectile(
                    16,
                    2,
                    1.2f,
                    3.0f,
                    0.04f,
                    1,
                    0.0f,   // ノックバック無し
                    true,
                    10.0,    // preferredMinRange
                    16.0,    // preferredMaxRange
                    100,             // magSize
                    24,             // reloadTicks
                    0,              // reloadStartAmmo
                    5.0,
                    false
            );

            // キサキ：中〜遠距離（命中寄り）
            case KISAKI -> WeaponSpec.projectile(
                    16,
                    2,
                    1.2f,
                    3.0f,
                    0.04f,
                    1,
                    0.0f,   // ノックバック無し
                    true,
                    5.0,    // preferredMinRange
                    10.0,    // preferredMaxRange
                    50,             // magSize
                    37,             // reloadTicks
                    0,              // reloadStartAmmo
                    5.0,
                    false
            );

            // アリス：遠距離 高ダメ（後でレールガンに変更しやすい）
            case ALICE -> WeaponSpec.projectile(
                    40,
                    60,      // cooldownTicks 長め（=チャージ間隔扱い）
                    18f,
                    3.5f,    // projectileSpeed 遅め
                    0f,
                    1,
                    1.5f,
                    true,
                    10.0,
                    18.0,
                    1,       // magSize
                    0,       // reloadTicks
                    0,       // reloadStartAmmo
                    6.0,     // panicRange
                    true     // infiniteAmmo
            );
        };
    }
}
