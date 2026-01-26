package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public class ModItemGroups {
    public static ItemGroup BLUE_STUDENT_GROUP;

    public static void register() {
        BLUE_STUDENT_GROUP = Registry.register(
                Registries.ITEM_GROUP,
                BlueStudentMod.id("blue_student"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(BlueStudentMod.TABLET_BLOCK_ITEM))
                        .displayName(Text.translatable("itemGroup.blue_student"))
                        .entries((ctx, entries) -> {
                            entries.add(BlueStudentMod.TABLET_BLOCK_ITEM);

                            // もし将来追加するならここに並べる
                            // entries.add(BlueStudentMod.SHIROKO_BED_ITEM);
                            // entries.add(BlueStudentMod.HINA_BED_ITEM);
                        })
                        .build()
        );
    }
}