package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

// ▼追加 import
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

public class ShirokoModel extends GeoModel<ShirokoEntity> {

    @Override
    public Identifier getModelResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "geo/shiroko.geo.json");
    }

    @Override
    public Identifier getTextureResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "textures/entity/shiroko.png");
    }

    @Override
    public Identifier getAnimationResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "animations/shiroko.animation.json");
    }

    // ▼ここを追加
    @Override
    public void setCustomAnimations(ShirokoEntity animatable, long instanceId, AnimationState<ShirokoEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone head = this.getAnimationProcessor().getBone("Head");
        CoreGeoBone arm  = this.getAnimationProcessor().getBone("Arm");

        // entity の pitch は「上下視線」そのもの
        float pitchDeg = animatable.getPitch();
        float pitchRad = pitchDeg * ((float) Math.PI / 180F);

        if (head != null) head.setRotX(pitchRad * 0.5f);
        if (arm  != null) arm.setRotX(pitchRad * 0.9f);
    }
}