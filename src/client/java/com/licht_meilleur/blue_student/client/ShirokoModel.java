package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.object.DataTicket;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

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

    @Override
    public void setCustomAnimations(ShirokoEntity animatable, long instanceId, AnimationState<ShirokoEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone head = this.getAnimationProcessor().getBone("Head");
        CoreGeoBone arm  = this.getAnimationProcessor().getBone("Arm");

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

        // GeckoLibは度→ラジアン変換が必要
        float pitchRad = data.headPitch() * ((float)Math.PI / 180F);
        float yawRad   = data.netHeadYaw() * ((float)Math.PI / 180F);

        if (head != null) {
            head.setRotX(pitchRad * 0.5f);
            head.setRotY(yawRad   * 0.5f);
        }
        if (arm != null) {
            // 腕は上下だけ強め、左右は弱め みたいに調整すると自然
            arm.setRotX(pitchRad * 0.9f);
            arm.setRotY(yawRad   * 0.2f);
        }
    }
}
