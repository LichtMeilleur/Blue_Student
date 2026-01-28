package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.LivingEntity;

public interface WeaponAction {
    /**
     * サーバー側で呼ぶ。
     * @return 発射したら true（クールダウンに入る）
     */
    boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec);
}