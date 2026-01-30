package com.licht_meilleur.blue_student.student;

import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public interface IStudentEntity {
    StudentId getStudentId();

    StudentAiMode getAiMode();
    void setAiMode(StudentAiMode mode);

    BlockPos getSecurityPos();
    void setSecurityPos(BlockPos pos);

    UUID getOwnerUuid();
    void setOwnerUuid(UUID uuid);

    Inventory getStudentInventory();

    // ===== ammo/reload =====
    int getAmmoInMag();
    void consumeAmmo(int amount);

    boolean isReloading();
    void startReload(WeaponSpec spec);
    void tickReload(WeaponSpec spec);
    void queueFire(LivingEntity target);
    LivingEntity consumeQueuedFireTarget(); // 取ったら消える
    boolean hasQueuedFire();


    // ===== animation / presentation hooks =====
    default void requestShot() {}
    default void requestShot(LivingEntity target) { requestShot(); } // ★追加
    default void requestReload() {}

    default void requestDodge() {}
    default void requestJump() {}
    default void requestFall() {}
    default void requestExit() {}
    default void requestSwim() {}
    default void requestSit() {}
}
