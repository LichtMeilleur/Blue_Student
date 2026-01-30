package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.bed.BedLinkEvents;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.block.TabletBlock;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.block.entity.TabletBlockEntity;
import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.HoshinoEntity;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import com.licht_meilleur.blue_student.entity.projectile.StudentBulletEntity;
import com.licht_meilleur.blue_student.item.OnlyBedItem;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class BlueStudentMod implements ModInitializer {
    public static final String MOD_ID = "blue_student";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    // ===== EntityType =====
    public static final EntityType<ShirokoEntity> SHIROKO = Registry.register(
            Registries.ENTITY_TYPE,
            id("shiroko"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ShirokoEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build()
    );
    public static final EntityType<HoshinoEntity> HOSHINO = Registry.register(
            Registries.ENTITY_TYPE,
            id("hoshino"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, HoshinoEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build()
    );

    // ===== OnlyBed =====
    public static final OnlyBedBlock ONLY_BED_BLOCK = Registry.register(
            Registries.BLOCK, id("only_bed"),
            new OnlyBedBlock(AbstractBlock.Settings.create().strength(1.0f).nonOpaque())
    );

    public static final BlockEntityType<OnlyBedBlockEntity> ONLY_BED_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id("only_bed"),
            BlockEntityType.Builder.create(OnlyBedBlockEntity::new, ONLY_BED_BLOCK).build(null)
    );

    public static final Item SHIROKO_BED_ITEM = Registry.register(
            Registries.ITEM, id("shiroko_bed"),
            new OnlyBedItem(ONLY_BED_BLOCK, StudentId.SHIROKO, new Item.Settings().maxCount(64))
    );
    public static final Item HINA_BED_ITEM = Registry.register(
            Registries.ITEM, id("hina_bed"),
            new OnlyBedItem(ONLY_BED_BLOCK, StudentId.HINA, new Item.Settings().maxCount(64))
    );
    public static final Item HOSHINO_BED_ITEM = Registry.register(
            Registries.ITEM, id("hoshino_bed"),
            new OnlyBedItem(ONLY_BED_BLOCK, StudentId.HOSHINO, new Item.Settings().maxCount(64))
    );
    public static final Item KISAKI_BED_ITEM = Registry.register(
            Registries.ITEM, id("kisaki_bed"),
            new OnlyBedItem(ONLY_BED_BLOCK, StudentId.KISAKI, new Item.Settings().maxCount(64))
    );
    public static final Item ALICE_BED_ITEM = Registry.register(
            Registries.ITEM, id("alice_bed"),
            new OnlyBedItem(ONLY_BED_BLOCK, StudentId.ALICE, new Item.Settings().maxCount(64))
    );

    // ===== Tablet =====
    public static final Block TABLET_BLOCK = Registry.register(
            Registries.BLOCK, id("tablet"),
            new TabletBlock(AbstractBlock.Settings.create().strength(1.0f).nonOpaque())
    );

    public static final Item TABLET_BLOCK_ITEM = Registry.register(
            Registries.ITEM, id("tablet"),
            new BlockItem(TABLET_BLOCK, new Item.Settings().maxCount(64))
    );

    // BlockEntityType
    public static final BlockEntityType<TabletBlockEntity> TABLET_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(MOD_ID, "tablet"),
            BlockEntityType.Builder.create(TabletBlockEntity::new, TABLET_BLOCK).build(null)
    );

    @Override
    public void onInitialize() {
        System.out.println("[BlueStudent] onInitialize start");
        LOGGER.info("[BlueStudent] onInitialize start");

        FabricDefaultAttributeRegistry.register(SHIROKO, AbstractStudentEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HOSHINO, AbstractStudentEntity.createAttributes());

        ModScreenHandlers.register();

        System.out.println("[BlueStudent] before ModPackets.registerC2S");
        LOGGER.info("[BlueStudent] before ModPackets.registerC2S");
        ModPackets.registerC2S();
        System.out.println("[BlueStudent] after ModPackets.registerC2S");
        LOGGER.info("[BlueStudent] after ModPackets.registerC2S");

        BedLinkEvents.register();
        com.licht_meilleur.blue_student.registry.ModItemGroups.register();
    }

    // StudentId -> bed item
    public static Item getBedItemFor(StudentId id) {
        return switch (id) {
            case SHIROKO -> SHIROKO_BED_ITEM;
            case HOSHINO -> HOSHINO_BED_ITEM;
            case HINA -> HINA_BED_ITEM;
            case ALICE -> ALICE_BED_ITEM;
            case KISAKI -> KISAKI_BED_ITEM;
        };
    }
    /** client側でだけセットされる。サーバーでは null のまま */
    public static Consumer<BlockPos> OPEN_TABLET_SCREEN = null;
    public static final EntityType<StudentBulletEntity> STUDENT_BULLET = Registry.register(
            Registries.ENTITY_TYPE,
            id("student_bullet"),
            FabricEntityTypeBuilder.<StudentBulletEntity>create(SpawnGroup.MISC, StudentBulletEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );
}
