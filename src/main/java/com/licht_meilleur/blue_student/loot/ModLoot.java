package com.licht_meilleur.blue_student.loot;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;

public class ModLoot {
    // 例：ダンジョン、廃坑、要塞など
    private static final Identifier DUNGEON = new Identifier("minecraft", "chests/simple_dungeon");
    private static final Identifier MINESHAFT = new Identifier("minecraft", "chests/abandoned_mineshaft");
    private static final Identifier STRONGHOLD = new Identifier("minecraft", "chests/stronghold_corridor");

    public static void init() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (id.equals(DUNGEON) || id.equals(MINESHAFT) || id.equals(STRONGHOLD)) {

                LootPool pool = LootPool.builder()
                        // 何回抽選するか：1回
                        .rolls(UniformLootNumberProvider.create(0, 1))
                        // Ticket
                        .with(ItemEntry.builder(BlueStudentMod.TICKET)
                                // 重み（出やすさ）
                                .weight(3))
                        .build();

                tableBuilder.pool(pool);
            }
        });

        BlueStudentMod.LOGGER.info("[BlueStudent] ModLoot init");
    }
}