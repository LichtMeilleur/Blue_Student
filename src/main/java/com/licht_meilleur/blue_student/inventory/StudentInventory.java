package com.licht_meilleur.blue_student.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

public class StudentInventory implements Inventory {
    private final DefaultedList<ItemStack> stacks;
    private final Runnable markDirtyCallback;




    private final SimpleInventory equipInv = new SimpleInventory(1); // ★追加

    public StudentInventory(int size, Runnable markDirtyCallback) {
        this.stacks = DefaultedList.ofSize(size, ItemStack.EMPTY);
        this.markDirtyCallback = markDirtyCallback;
    }

    public StudentInventory(Runnable markDirtyCallback) {
        this(9, markDirtyCallback);
    }

    public DefaultedList<ItemStack> getStacks() {
        return stacks;
    }

    public void writeNbt(NbtCompound nbt) {
        Inventories.writeNbt(nbt, stacks);

        // ★追加：装備スロット(1)を保存
        NbtCompound equipTag = new NbtCompound();
        equipTag.put("slot0", equipInv.getStack(0).writeNbt(new NbtCompound()));
        nbt.put("equipInv", equipTag);
    }

    public void readNbt(NbtCompound nbt) {
        Inventories.readNbt(nbt, stacks);

        // ★追加：装備スロット(1)を復元
        if (nbt.contains("equipInv")) {
            NbtCompound equipTag = nbt.getCompound("equipInv");
            if (equipTag.contains("slot0")) {
                equipInv.setStack(0, ItemStack.fromNbt(equipTag.getCompound("slot0")));
            } else {
                equipInv.setStack(0, ItemStack.EMPTY);
            }
        } else {
            equipInv.setStack(0, ItemStack.EMPTY);
        }
    }

    @Override
    public int size() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : stacks) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(stacks, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(stacks, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public void markDirty() {
        if (markDirtyCallback != null) markDirtyCallback.run();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        stacks.clear();
        markDirty();
    }

    public Inventory getEquipInv() { return equipInv; }

    public ItemStack getBrEquipStack() { return equipInv.getStack(0); }
}