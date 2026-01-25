package com.licht_meilleur.blue_student.inventory;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class StudentScreenHandler extends ScreenHandler {
    private final Inventory studentInv;
    public final ShirokoEntity entity;   // 画面表示用（HP/名前/矢印など）
    public final int entityId;

    private static final int SLOT_START_X = 30;
    private static final int SLOT_START_Y = 72;
    private static final int SLOT_SIZE = 18;

    // ===== サーバー側：本物のentity + 本物のinventory =====
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, ShirokoEntity entity) {
        this(syncId, playerInv, entity, entity.getStudentInventory());
    }

    // ===== クライアント側：entity(あれば) + ダミーinventory(9) =====
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, ShirokoEntity entity, Inventory inv9) {
        super(ModScreenHandlers.STUDENT_MENU, syncId);
        this.entity = entity;
        this.entityId = (entity != null) ? entity.getId() : -1;
        this.studentInv = inv9;

        // student 3x3
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(studentInv, i++, SLOT_START_X + col * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE));
            }
        }

        // player inv
        int playerInvX = 8;
        int playerInvY = 140;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }

        // hotbar
        int hotbarY = playerInvY + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, playerInvX + col * 18, hotbarY));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (entity != null) return entity.isAlive() && player.distanceTo(entity) < 8.0f;

        // entityがnullの可能性に備える（安全策）
        var e = player.getWorld().getEntityById(entityId);
        return e != null && e.isAlive() && player.distanceTo(e) < 8.0f;
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