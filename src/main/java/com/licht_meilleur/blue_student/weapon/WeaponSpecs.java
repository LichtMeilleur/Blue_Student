package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentForm;
import com.licht_meilleur.blue_student.student.StudentId;

public class WeaponSpecs {

    // =========================
    // 既存（通常武器）を “定数” にする
    // =========================

    // シロコ：遠距離 標準AR想定
    private static final WeaponSpec SHIROKO_MAIN = WeaponSpec.projectile(
            16, 2, 1.2f, 3.0f, 0.04f, 1, 0.0f, true,
            5.0, 14.0,
            30, 20, 0, 3.5, false,
            WeaponSpec.FxType.BULLET, 1.0f, 6,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // ホシノ：通常（ショットガン）
    private static final WeaponSpec HOSHINO_MAIN = WeaponSpec.projectile(
            10, 15, 3.0f, 2.0f, 0.25f, 8, 0.8f, true,
            2.0, 8.0,
            5, 43, 0, 1.5, false,
            WeaponSpec.FxType.SHOTGUN, 3.0f, 12,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // ヒナ：遠距離 高レート
    private static final WeaponSpec HINA_MAIN = WeaponSpec.projectile(
            16, 2, 1.2f, 3.0f, 0.04f, 1, 0.0f, true,
            7.0, 16.0,
            100, 24, 0, 3.5, false,
            WeaponSpec.FxType.BULLET, 1.0f, 6,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // キサキ：中〜遠距離（命中寄り）
    private static final WeaponSpec KISAKI_MAIN = WeaponSpec.projectile(
            16, 2, 1.2f, 3.0f, 0.04f, 1, 0.0f, true,
            4.0, 10.0,
            50, 37, 0, 3.5, false,
            WeaponSpec.FxType.BULLET, 1.0f, 6,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // アリス：通常（レールガン）
    private static final WeaponSpec ALICE_MAIN = WeaponSpec.hitscan(
            40, 20, 18f, 0f, 0f, 1, 1.5f, true,
            8.0, 18.0,
            1, 20, 0, 3.5, true,
            WeaponSpec.FxType.RAILGUN, 2.0f, 18,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // =========================
    // 特殊（例：アリスHYPER）
    // =========================
    public static final WeaponSpec ALICE_HYPER = WeaponSpec.hitscan(
            48, 80, 14f, 0f, 0f, 1, 0f, true,
            0, 48,
            1, 1, 0, 0, true,
            WeaponSpec.FxType.RAILGUN_HYPER, 1.0f, 20,   WeaponSpec.MuzzleLocator.MUZZLE
    );

    // =========================
    // BR（ホシノ）
    // =========================
    private static final WeaponSpec HOSHINO_BR_MAIN = WeaponSpec.projectile(
            12, 10, 3.5f, 2.2f, 0.22f, 8, 0.9f, true,
            2.5, 8.0,
            5, 35, 0, 4.0, false,
            WeaponSpec.FxType.SHOTGUN, 3.0f, 12,WeaponSpec.MuzzleLocator.MUZZLE
    );

    // ★BRサブ（ハンドガン）※hitscanの引数は ALICE_MAIN と同じ並びで書く
    private static final WeaponSpec HOSHINO_BR_SUB = WeaponSpec.hitscan(
            16, 0, 1.4f,
            0f, 0.04f,
            1,
            0.0f,
            true,
            1.5, 14.0,
            15,
            25,
            0,
            3.5,
            true,
            WeaponSpec.FxType.BULLET,
            1.0f,
            2,
            WeaponSpec.MuzzleLocator.SUB_MUZZLE

    );

    private static final WeaponSpec ALICE_BR_MAIN = WeaponSpec.projectile(
            16, 2, 2.5f, 2.2f, 0.22f, 8, 0.5f, true,
            2.5, 12.0,
            5, 35, 0, 4.0, false,
            WeaponSpec.FxType.RAILGUN, 3.0f, 12,WeaponSpec.MuzzleLocator.MUZZLE
    );

    private static final WeaponSpec ALICE_BR_SUB_L = WeaponSpec.hitscan(
            18, 30, 5.0f,
            0f, 0.04f,
            1,
            0.0f,
            true,
            1.5, 14.0,
            15,
            25,
            0,
            3.5,
            true,
            WeaponSpec.FxType.BULLET,
            1.5f,
            8,
            WeaponSpec.MuzzleLocator.LEFT_SUB_MUZZLE

    );
    private static final WeaponSpec ALICE_BR_SUB_R = WeaponSpec.hitscan(
            18, 30, 5.0f,
            0f, 0.04f,
            1,
            0.0f,
            true,
            1.5, 14.0,
            15,
            25,
            0,
            3.5,
            true,
            WeaponSpec.FxType.BULLET,
            1.5f,
            8,
            WeaponSpec.MuzzleLocator.RIGHT_SUB_MUZZLE

    );

    // =========================
    // 既存API：通常フォーム用
    // =========================
    public static WeaponSpec forStudent(StudentId id) {
        return switch (id) {
            case SHIROKO -> SHIROKO_MAIN;
            case HOSHINO -> HOSHINO_MAIN;
            case HINA    -> HINA_MAIN;
            case KISAKI  -> KISAKI_MAIN;
            case ALICE   -> ALICE_MAIN;
        };
    }

    // =========================
    // 新API：フォーム＋サブ判定
    // =========================
    public static WeaponSpec forStudent(StudentId id, StudentForm form, IStudentEntity.FireChannel ch) {
        if (ch == null) ch = IStudentEntity.FireChannel.MAIN;

        return switch (id) {
            case HOSHINO -> {
                if (form == StudentForm.BR) {
                    // ホシノBRはSUB_L/SUB_Rどちらでも sub_muzzle でOK
                    yield (ch == IStudentEntity.FireChannel.MAIN) ? HOSHINO_BR_MAIN : HOSHINO_BR_SUB;
                } else {
                    yield HOSHINO_MAIN; // 通常はサブなし
                }
            }

            case ALICE -> {
                if (form == StudentForm.BR) {
                    yield switch (ch) {
                        case MAIN  -> ALICE_BR_MAIN;
                        case SUB_L -> ALICE_BR_SUB_L;
                        case SUB_R -> ALICE_BR_SUB_R;
                    };
                } else {
                    yield ALICE_MAIN;
                }
            }

            default -> forStudent(id);
        };
    }
}