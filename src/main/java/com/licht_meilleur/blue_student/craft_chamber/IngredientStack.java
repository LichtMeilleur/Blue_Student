package com.licht_meilleur.blue_student.craft_chamber;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public record IngredientStack(Item item, int count) {
    public ItemStack toStack() {
        return new ItemStack(item, count);
    }
}