package com.licht_meilleur.blue_student.inventory;

import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class StudentScreenHandler extends ScreenHandler {
    private final Inventory studentInv;

    // ★共通インターフェースで保持
    public final IStudentEntity entity;   // クライアントではnullの場合あり
    public final int entityId;

    private static final int SLOT_START_X = 48;
    private static final int SLOT_START_Y = 90;
    private static final int SLOT_SIZE = 18;

    // ===== サーバー側：本物のentity + 本物のinventory =====
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, IStudentEntity entity) {
        this(syncId, playerInv, entity, entity.getStudentInventory());
    }

    // ===== クライアント側：entity(あれば) + ダミーinventory(9) =====
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, IStudentEntity entity, Inventory inv9) {
        super(ModScreenHandlers.STUDENT_MENU, syncId);
        this.entity = entity;
        this.entityId = (entity instanceof Entity e) ? e.getId() : -1;
        this.studentInv = inv9;

        // student 3x3
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(studentInv, i++, SLOT_START_X + col * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE));
            }
        }

        // StudentScreenHandler の中：player inventory を消して hotbar だけ残す例
        int hotbarX = 48;
        int hotbarY = 256 - 24; // BG下端ギリギリに寄せる（好みで調整）

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, hotbarX + col * 18, hotbarY));
        }
    }

    /** クライアント側でも entity を引けるようにする（StudentScreen からも使える） */
    public IStudentEntity resolveEntity(PlayerEntity player) {
        if (entity != null) return entity;
        Entity raw = player.getWorld().getEntityById(entityId);
        return (raw instanceof IStudentEntity se) ? se : null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        IStudentEntity se = resolveEntity(player);
        if (!(se instanceof Entity e)) return false;
        return e.isAlive() && player.distanceTo(e) < 8.0f;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack original = slot.getStack();
        newStack = original.copy();

        int studentSize = studentInv.size(); // 9
        int totalSlots = this.slots.size();

        if (index < studentSize) {
            if (!this.insertItem(original, studentSize, totalSlots, true)) return ItemStack.EMPTY;
        } else {
            if (!this.insertItem(original, 0, studentSize, false)) return ItemStack.EMPTY;
        }

        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();

        return newStack;
    }
}
