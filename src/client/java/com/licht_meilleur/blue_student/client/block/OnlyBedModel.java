package com.licht_meilleur.blue_student.client.block;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class OnlyBedModel extends GeoModel<OnlyBedBlockEntity> {

    @Override
    public Identifier getModelResource(OnlyBedBlockEntity animatable) {
        return BlueStudentMod.id("geo/only_for_bed.geo.json");
    }

    @Override
    public Identifier getAnimationResource(OnlyBedBlockEntity animatable) {
        return BlueStudentMod.id("animations/only_for_bed.animation.json");
    }

    @Override
    public Identifier getTextureResource(OnlyBedBlockEntity animatable) {
        // BlockStateから student を読む
        var state = animatable.getCachedState();
        StudentId id = (state != null && state.contains(OnlyBedBlock.STUDENT))
                ? state.get(OnlyBedBlock.STUDENT)
                : StudentId.SHIROKO;

        // ★ファイル名はスペース禁止：shiroko_bed.png などにしておく
        return switch (id) {
            case SHIROKO -> BlueStudentMod.id("textures/entity/shiroko_bed.png");
            case HOSHINO -> BlueStudentMod.id("textures/entity/hoshino_bed.png");
            case HINA    -> BlueStudentMod.id("textures/entity/hina_bed.png");
            case ALICE   -> BlueStudentMod.id("textures/entity/alice_bed.png");
            case KISAKI  -> BlueStudentMod.id("textures/entity/kisaki_bed.png");
        };
    }
}