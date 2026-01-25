package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

// ▼追加 import
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.DataTicket;
import software.bernie.geckolib.core.object.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;

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

        // GeckoLibが渡してくれる「視線データ（headYaw/headPitch）」を使う
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) return;

        GeoBone head = this.getAnimationProcessor().getBone("Head");
        GeoBone arm  = this.getAnimationProcessor().getBone("Arm");

        // 度 → ラジアン
        float pitchRad = data.headPitch() * ((float)Math.PI / 180F);

        // 体の上下をボーンで表現：Headは控えめ、Armは強め
        if (head != null) head.setRotX(pitchRad * 0.5f);
        if (arm  != null) arm.setRotX(pitchRad * 0.9f);
    }
}