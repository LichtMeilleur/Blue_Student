package com.licht_meilleur.blue_student.client.projectile;

import com.licht_meilleur.blue_student.entity.projectile.HyperCannonEntity;
import com.licht_meilleur.blue_student.entity.projectile.OldHyperCannonEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
/*
public class HyperCannonRenderer extends GeoEntityRenderer<OldHyperCannonEntity> {
    public HyperCannonRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HyperCannonModel());
        this.shadowRadius = 0.0f;

    }

    public void render(OldHyperCannonEntity entity,
                       float entityYaw,
                       float partialTick,
                       MatrixStack matrixStack,
                       VertexConsumerProvider bufferSource,
                       int packedLight) {

        matrixStack.push();

        // ★モデル前方向が北(-Z)なので 180°補正
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));

        super.render(entity, entityYaw, partialTick, matrixStack, bufferSource, packedLight);

        matrixStack.pop();
    }

}

 */