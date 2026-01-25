package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import com.licht_meilleur.blue_student.block.TabletBlock;
import com.licht_meilleur.blue_student.block.entity.TabletBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.block.entity.BlockEntityType;

public class BlueStudentMod implements ModInitializer {
    public static final String MOD_ID = "blue_student";

    public static final EntityType<ShirokoEntity> SHIROKO = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "shiroko"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ShirokoEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .build()
    );

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(SHIROKO, ShirokoEntity.createAttributes());
        ModScreenHandlers.register();
        ModPackets.registerC2S();
    }



    public static final Block TABLET_BLOCK = Registry.register(
            Registries.BLOCK,
            new Identifier(MOD_ID, "tablet"),
            new TabletBlock(AbstractBlock.Settings.create().strength(1.0f).nonOpaque())
    );

    public static final Item TABLET_BLOCK_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "tablet"),
            new BlockItem(TABLET_BLOCK, new Item.Settings().maxCount(64))
    );

    // BlockEntityType
    public static final BlockEntityType<TabletBlockEntity> TABLET_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(MOD_ID, "tablet"),
            BlockEntityType.Builder.create(TabletBlockEntity::new, TABLET_BLOCK).build(null)
    );

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}