package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.HoshinoEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class HoshinoModel extends GeoModel<HoshinoEntity> {

    @Override
    public Identifier getModelResource(HoshinoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "geo/hoshino.geo.json");
    }

    @Override
    public Identifier getTextureResource(HoshinoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "textures/entity/hoshino.png");
    }

    @Override
    public Identifier getAnimationResource(HoshinoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "animations/hoshino.animation.json");
    }

    @Override
    public void setCustomAnimations(HoshinoEntity animatable, long instanceId, AnimationState<HoshinoEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone head = this.getAnimationProcessor().getBone("Head");
        CoreGeoBone arm  = this.getAnimationProcessor().getBone("Arm");

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) return;

        // --- 角度（度）を先にclampしてからラジアンへ ---
        // 頭：左右±60°, 上30°/下40°
        float headYawDeg   = clamp(data.netHeadYaw(), -60f, 60f);
        float headPitchDeg = clamp(data.headPitch(), -40f, 30f);

        // 腕：左右±35°, 上45°/下20°（銃っぽい範囲）
        float armYawDeg    = clamp(data.netHeadYaw(), -35f, 35f);
        float armPitchDeg  = clamp(data.headPitch(), -20f, 45f);

        float headPitchRad = degToRad(headPitchDeg);
        float headYawRad   = degToRad(headYawDeg);
        float armPitchRad  = degToRad(armPitchDeg);
        float armYawRad    = degToRad(armYawDeg);

        if (head != null) {
            head.setRotX(headPitchRad * 0.5f);
            head.setRotY(headYawRad   * 0.5f);
        }
        if (arm != null) {
            arm.setRotX(armPitchRad * 0.9f);
            arm.setRotY(armYawRad   * 0.2f);
        }
    }

    private static float degToRad(float deg) {
        return deg * ((float)Math.PI / 180F);
    }
    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

}
