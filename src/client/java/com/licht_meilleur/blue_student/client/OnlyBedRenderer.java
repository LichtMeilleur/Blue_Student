package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class OnlyBedRenderer extends GeoBlockRenderer<OnlyBedBlockEntity> {
    public OnlyBedRenderer() {
        super(new OnlyBedModel());
    }
    @Override
    public Identifier getTextureLocation(OnlyBedBlockEntity animatable) {

        BlockState st = animatable.getCachedState();
        StudentId sid = st.get(OnlyBedBlock.STUDENT);

        return switch (sid) {
            case SHIROKO -> BlueStudentMod.id("textures/entity/shiroko_bed.png");
            case HOSHINO -> BlueStudentMod.id("textures/entity/hoshino_bed.png");
            case HINA    -> BlueStudentMod.id("textures/entity/hina_bed.png");
            case ALICE   -> BlueStudentMod.id("textures/entity/alice_bed.png");
            case KISAKI  -> BlueStudentMod.id("textures/entity/kisaki_bed.png");
        };
    }
}