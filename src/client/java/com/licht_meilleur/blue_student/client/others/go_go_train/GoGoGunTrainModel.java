package com.licht_meilleur.blue_student.client.others.go_go_train;

import com.licht_meilleur.blue_student.entity.go_go_train.GoGoGunTrainEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class GoGoGunTrainModel extends GeoModel<GoGoGunTrainEntity> {

    @Override
    public Identifier getModelResource(GoGoGunTrainEntity animatable) {
        return id("geo/gun_train.geo.json");
    }

    @Override
    public Identifier getTextureResource(GoGoGunTrainEntity animatable) {
        return id("textures/entity/gun_train.png");
    }

    @Override
    public Identifier getAnimationResource(GoGoGunTrainEntity animatable) {
        return id("animations/gun_train.animation.json");
    }

    // ★回らない時はまず軸を疑う：1=Y, 2=Z, 3=X
    private static final int   SHEET_AXIS = 1;
    private static final float SHEET_SIGN = -1.0f;
    private static final float SHEET_OFFSET_DEG = 0.0f;

    @Override
    public void setCustomAnimations(GoGoGunTrainEntity animatable, long instanceId, AnimationState<GoGoGunTrainEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);

        var sheet = this.getAnimationProcessor().getBone("sheet");
        if (sheet == null) return;

        // 残留消し
        sheet.setRotX(0f);
        sheet.setRotY(0f);
        sheet.setRotZ(0f);

        // ★相対をやめて「絶対Yaw」で回す
        float aimYawDeg = animatable.getSheetYawDeg(); // サーバーで入れてるやつ
        float aimRad = aimYawDeg * MathHelper.RADIANS_PER_DEGREE * (-1.0f); // SIGNは必要に応じて反転

        sheet.setRotY(aimRad);
    }

}