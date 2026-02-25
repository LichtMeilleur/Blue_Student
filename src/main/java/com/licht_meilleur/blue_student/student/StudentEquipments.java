package com.licht_meilleur.blue_student.student;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public final class StudentEquipments {


    // ★追加：装備スロットの背景画像（生徒ごとに差し替え）
    public static Identifier getBrSlotTexture(StudentId sid) {
        return switch (sid) {
            case HOSHINO -> BlueStudentMod.id("textures/gui/hoshino_br_equip_item.png");
            // ALICE 未実装なら empty に落とす
             case ALICE -> BlueStudentMod.id("textures/gui/alice_br_equip_item.png");
            default -> BlueStudentMod.id("textures/gui/empty_br.png");
        };
    }

    /** その生徒にBR装備スロットを表示するか（=BR実装済みか） */
    public static boolean supportsBr(StudentId sid) {
        return switch (sid) {
            case HOSHINO -> true;
            case ALICE -> true; // アリスBR出来たらON
            default -> false;
        };
    }
    private StudentEquipments() {

    }

    public static boolean isBrEquipped(StudentId sid, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!sid.hasBrForm()) return false;

        return switch (sid) {
            case HOSHINO -> stack.isOf(BlueStudentMod.HOSHINO_BR_EQUIP_ITEM);
            case ALICE   -> stack.isOf(BlueStudentMod.ALICE_BR_EQUIP_ITEM);
            default      -> false;
        };
    }
}