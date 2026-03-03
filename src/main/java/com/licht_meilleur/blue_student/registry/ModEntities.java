package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.GunTrainEntity;
import com.licht_meilleur.blue_student.entity.projectile.GunTrainShellEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import com.licht_meilleur.blue_student.entity.projectile.HyperCannonEntity;
import com.licht_meilleur.blue_student.entity.projectile.SonicBeamEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private ModEntities() {}

    public static EntityType<HyperCannonEntity> HYPER_CANNON;
    public static EntityType<TrainEntity> TRAIN;
    public static EntityType<GunTrainEntity> GUN_TRAIN;

    public static void register() {
        if (HYPER_CANNON != null) return; // 二重呼び防止

        HYPER_CANNON = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(BlueStudentMod.MOD_ID, "hyper_cannon"),
                FabricEntityTypeBuilder.<HyperCannonEntity>create(
                                SpawnGroup.MISC,
                                (type, world) -> new HyperCannonEntity(type, world)
                        )
                        .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                        .trackRangeBlocks(64)
                        .trackedUpdateRate(10)
                        .build()
        );
                TRAIN = Registry.register(
                        Registries.ENTITY_TYPE,
                        BlueStudentMod.id("train"),
                        FabricEntityTypeBuilder.<TrainEntity>create(SpawnGroup.MISC, TrainEntity::new)
                                .dimensions(EntityDimensions.fixed(1.2f, 1.1f)) // 好みで
                                .trackRangeBlocks(64)
                                .trackedUpdateRate(1)
                                .build()
                );

        GUN_TRAIN = Registry.register(
                Registries.ENTITY_TYPE,
                BlueStudentMod.id("gun_train"),
                FabricEntityTypeBuilder.<GunTrainEntity>create(SpawnGroup.MISC, GunTrainEntity::new)
                        .dimensions(EntityDimensions.fixed(1.2f, 1.1f))
                        .trackRangeBlocks(64)
                        .trackedUpdateRate(1)
                        .build()
        );

    }
    public static final EntityType<SonicBeamEntity> SONIC_BEAM =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    BlueStudentMod.id("sonic_beam"),
                    FabricEntityTypeBuilder
                            .<SonicBeamEntity>create(SpawnGroup.MISC, SonicBeamEntity::new)
                            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
    public static final EntityType<GunTrainShellEntity> GUN_TRAIN_SHELL =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    BlueStudentMod.id("gun_train_shell"),
                    FabricEntityTypeBuilder.<GunTrainShellEntity>create(SpawnGroup.MISC, GunTrainShellEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
}