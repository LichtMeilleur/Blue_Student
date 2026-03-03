package com.licht_meilleur.blue_student.craft_chamber;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

public final class CraftChamberRecipes {
    private CraftChamberRecipes() {}

    public static final List<CraftChamberRecipe> ALL = List.of(
            new CraftChamberRecipe(
                    BlueStudentMod.id("hoshino_br_equip"),
                    new ItemStack(BlueStudentMod.HOSHINO_BR_EQUIP_ITEM, 1),
                    List.of(
                            new IngredientStack(Items.NETHERITE_INGOT, 5),
                            new IngredientStack(Items.LEATHER, 6),
                            new IngredientStack(Items.STRING, 6),
                            new IngredientStack(Items.IRON_INGOT, 2)
                    ),
                    new ItemStack(Items.NETHERITE_INGOT),
                    new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.STRING),
                    new ItemStack(Items.LEATHER)
            ),
            new CraftChamberRecipe(
                    BlueStudentMod.id("alice_br_equip"),
                    new ItemStack(BlueStudentMod.ALICE_BR_EQUIP_ITEM, 1),
                    List.of(
                            new IngredientStack(Items.NETHERITE_INGOT, 5),
                            new IngredientStack(Items.AMETHYST_SHARD, 2),
                            new IngredientStack(Items.FIREWORK_ROCKET, 2),
                            new IngredientStack(Items.IRON_INGOT, 8)
                    ),
                    new ItemStack(Items.NETHERITE_INGOT),
                    new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(Items.FIREWORK_ROCKET),
                    new ItemStack(Items.IRON_INGOT)
            )
    );

    public static CraftChamberRecipe byIndex(int idx) {
        return ALL.get(Math.floorMod(idx, ALL.size()));
    }
}