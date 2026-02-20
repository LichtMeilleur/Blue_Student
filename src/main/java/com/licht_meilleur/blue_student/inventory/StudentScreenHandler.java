package com.licht_meilleur.blue_student.inventory;

import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentEquipments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class StudentScreenHandler extends ScreenHandler {
    private final Inventory studentInv;
    private final Inventory equipInv; // ★追加：装備インベントリ(1)

    public final IStudentEntity entity;   // クライアントではnullの場合あり
    public final int entityId;

    private static final int SLOT_START_X = 48;
    private static final int SLOT_START_Y = 90;
    private static final int SLOT_SIZE = 18;

    private static final int EQUIP_X = 150;
    private static final int EQUIP_Y = 90;

    // 36枠の左上（背景画像の位置）
    private static final int EQUIP_BG_X = 150;
    private static final int EQUIP_BG_Y = 90;

    // Slot(16x16)を36枠の中央に配置
    private static final int EQUIP_SLOT_X = EQUIP_BG_X + 10;
    private static final int EQUIP_SLOT_Y = EQUIP_BG_Y + 10;

    // サーバー
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, IStudentEntity entity) {
        this(syncId, playerInv, entity,
                entity.getStudentInventory(),
                ((com.licht_meilleur.blue_student.inventory.StudentInventory)entity.getStudentInventory()).getEquipInv()
        );
    }

    // クライアント（ダミーinvを渡す）
    public StudentScreenHandler(int syncId, PlayerInventory playerInv, IStudentEntity entity, Inventory inv9, Inventory equipInv) {
        super(ModScreenHandlers.STUDENT_MENU, syncId);
        this.entity = entity;
        this.entityId = (entity instanceof Entity e) ? e.getId() : -1;
        this.studentInv = inv9;
        this.equipInv = equipInv;

        // student 3x3
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(studentInv, i++, SLOT_START_X + col * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE));
            }
        }

        // ★装備スロット（1個）
        // ★equip slot（supportsBrの時だけ）
        if (entity != null && StudentEquipments.supportsBr(entity.getStudentId())) {
            this.equipSlotScreenIndex = this.slots.size();
            this.addSlot(new Slot(equipInv, 0, EQUIP_SLOT_X, EQUIP_SLOT_Y) {
                @Override public boolean canInsert(ItemStack stack) {
                    return StudentEquipments.isBrEquipped(entity.getStudentId(), stack);
                }
                @Override public int getMaxItemCount() { return 1; }
            });
        }




        // hotbar だけ
        int hotbarX = 48;
        int hotbarY = 256 - 24;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, hotbarX + col * 18, hotbarY));
        }
    }

    /** クライアント側でも entity を引けるようにする */
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
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack original = slot.getStack();
        ItemStack newStack = original.copy();

        // スロット構成：
        // [0] equip(1)
        // [1..9] student 3x3
        // [10..18] hotbar
        int equipStart = 0;
        int equipEnd = 1;
        int studentStart = 1;
        int studentEnd = 10;
        int playerStart = 10;
        int playerEnd = this.slots.size();

        if (index < playerStart) {
            // 生徒側 -> プレイヤーへ
            if (!this.insertItem(original, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // プレイヤー -> まず装備に入るか判定 -> ダメなら生徒3x3へ
            IStudentEntity se = resolveEntity(player);
            boolean isEquip = (se != null) && StudentEquipments.isBrEquipped(se.getStudentId(), original);

            if (isEquip) {
                if (!this.insertItem(original, equipStart, equipEnd, false)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(original, studentStart, studentEnd, false)) return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();

        return newStack;
    }
    private int equipSlotScreenIndex = -1;

    public int getEquipSlotIndex() {
        return equipSlotScreenIndex;
    }
}
