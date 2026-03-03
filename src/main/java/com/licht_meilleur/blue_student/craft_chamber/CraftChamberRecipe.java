package com.licht_meilleur.blue_student.craft_chamber;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;

public record CraftChamberRecipe(
        Identifier id,
        ItemStack output,
        List<IngredientStack> costs,
        ItemStack slot12,
        ItemStack slot3,
        ItemStack slot6,
        ItemStack slot9
) {}