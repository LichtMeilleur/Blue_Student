package com.licht_meilleur.blue_student.client.others.go_go_train;

import com.licht_meilleur.blue_student.entity.go_go_train.GoGoTrainEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GoGoTrainRenderer extends GeoEntityRenderer<GoGoTrainEntity> {

    public GoGoTrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GoGoTrainModel());
        this.shadowRadius = 0f;
    }

    @Override
    protected void applyRotations(GoGoTrainEntity entity,
                                  MatrixStack matrices,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        // ★superは呼ばない（デフォルト回転が不明なので自前で確定）
        // super.applyRotations(entity, matrices, ageInTicks, rotationYaw, partialTick);

        // ★補間Yaw（クライアントが持ってる yaw/prevYaw を使う）
        float y = MathHelper.lerp(partialTick, entity.prevYaw, entity.getYaw());

        // ★モデル正面がズレるならここだけ定数で調整（0/90/180/-90を試す）
        float modelOffset = 0f;

        // Minecraft標準の向きに合わせて回す
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - y + modelOffset));
    }
}