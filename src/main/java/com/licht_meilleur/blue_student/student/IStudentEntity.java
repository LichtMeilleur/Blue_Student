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


    void requestLookTarget(net.minecraft.entity.LivingEntity target, int priority, int holdTicks);
    void requestLookAwayFrom(net.minecraft.entity.LivingEntity target, int priority, int holdTicks);
    void requestLookWorldDir(net.minecraft.util.math.Vec3d dir, int priority, int holdTicks);
    void requestLookMoveDir(int priority, int holdTicks);
    void requestLookPos(net.minecraft.util.math.Vec3d pos, int priority, int holdTicks);


    // AimGoalが読む
    LookRequest consumeLookRequest();
    // 毎tick、現在の要求（保持中なら保持）



    // ===== Evade state =====
    boolean isEvading();
    void setEvading(boolean v);




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



    // サブ射撃（sub_muzzle）用
    void queueFireSub(net.minecraft.entity.LivingEntity target);
    boolean hasQueuedFireSub();
    net.minecraft.entity.LivingEntity consumeQueuedFireSubTarget();

    // AimGoalが「今の射撃はサブか」を知る用（任意だが便利）
    boolean consumeQueuedFireIsSub();

    void requestBrAction(com.licht_meilleur.blue_student.student.StudentBrAction action, int holdTicks);




}

