package com.licht_meilleur.blue_student.weapon;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.LivingEntity;

public interface WeaponAction {
    /**
     * サーバー側で呼ぶ。
     * @return 発射したら true（クールダウンに入る）
     */
    boolean shoot(IStudentEntity shooter, LivingEntity target, WeaponSpec spec);


    default float calcDamage(IStudentEntity shooter, WeaponSpec spec) {
    float damage = spec.damage; // 例：元のダメージ

if (shooter instanceof AbstractStudentEntity se && se.hasKisakiSupportBuff()) {
        damage *= 1.25f; // キサキ支援倍率（好みで）
    }

// その damage を使って target.damage(...)
        return damage;
    }
}