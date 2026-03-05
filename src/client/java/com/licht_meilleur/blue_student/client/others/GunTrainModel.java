package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.GunTrainEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class GunTrainModel extends GeoModel<GunTrainEntity> {

    // ★モデル都合のオフセット（必要なら調整。まずは0で）
    private static final float SHEET_YAW_OFFSET_DEG = 0.0f;

    @Override
    public Identifier getModelResource(GunTrainEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "geo/gun_train.geo.json");
    }

    @Override
    public Identifier getTextureResource(GunTrainEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "textures/entity/gun_train.png");
    }

    @Override
    public Identifier getAnimationResource(GunTrainEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "animations/gun_train.animation.json");
    }

    private static final int SHEET_AXIS = 1; // 1=Y, 2=Z, 3=X
    private static final float SHEET_SIGN = -1.0f; // +1 or -1
    private static final float SHEET_OFFSET_DEG = 0.0f; // 0 / 90 / -90 / 180 を試す

    @Override
    public void setCustomAnimations(GunTrainEntity animatable, long instanceId, AnimationState<GunTrainEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);

        CoreGeoBone sheet = this.getAnimationProcessor().getBone("sheet");
        if (sheet == null) return;

        float bodyYawDeg = animatable.getBodyYawDegSynced(); // ★単体の車体Yaw（同期値）
        float aimYawDeg  = animatable.getSheetYawDeg();      // ★砲塔が向きたいYaw（同期値）

        float relDeg = MathHelper.wrapDegrees((aimYawDeg - bodyYawDeg) + SHEET_OFFSET_DEG);
        float relRad = relDeg * MathHelper.RADIANS_PER_DEGREE * SHEET_SIGN;

        // ★毎tick 他軸を0に戻して残留回転を消す
        sheet.setRotX(0);
        sheet.setRotY(0);
        sheet.setRotZ(0);

        if (SHEET_AXIS == 1) sheet.setRotY(relRad);
        else if (SHEET_AXIS == 2) sheet.setRotZ(relRad);
        else sheet.setRotX(relRad);
    }
}