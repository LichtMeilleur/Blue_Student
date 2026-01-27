package com.licht_meilleur.blue_student.student;

import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public interface IStudentEntity {
    StudentId getStudentId();

    Inventory getStudentInventory(); // ← StudentInventory固定をやめる

    UUID getOwnerUuid();
    void setOwnerUuid(UUID uuid);

    StudentAiMode getAiMode();
    void setAiMode(StudentAiMode mode);

    BlockPos getSecurityPos();
    void setSecurityPos(BlockPos pos);
}