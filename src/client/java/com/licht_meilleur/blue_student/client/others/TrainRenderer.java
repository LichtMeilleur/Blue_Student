package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TrainRenderer extends GeoEntityRenderer<TrainEntity> {

    public TrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new TrainModel());
        this.shadowRadius = 0f; // 影いらないなら
    }
    @Override
    protected void applyRotations(TrainEntity entity,
                                  net.minecraft.client.util.math.MatrixStack matrices,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, matrices, ageInTicks, rotationYaw, partialTick);

        // ★EntityのYawでモデルを回す（必要なら +180 / -90 調整）
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
    }
}
