package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.render.OverlayTexture;


public class StudentRenderer<T extends AbstractStudentEntity> extends GeoEntityRenderer<T> {

    public StudentRenderer(EntityRendererFactory.Context ctx, GeoModel<T> model, float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {

        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);

        // ===== muzzle =====
        GeoBone muzzle = this.getGeoModel().getBone("muzzle").orElse(null);
        if (muzzle != null) {
            Vec3d wp = new Vec3d(
                    entity.getX() + muzzle.getWorldPosition().x(),
                    entity.getY() + muzzle.getWorldPosition().y(),
                    entity.getZ() + muzzle.getWorldPosition().z()
            );
            entity.setClientMuzzleWorldPos(wp);
        }

        // ===== 右手アイテム表示 =====
        ItemStack stack = entity.getEatingStackForRender();
        if (stack.isEmpty()) return;

        GeoBone hand = this.getGeoModel().getBone("RightHandLocator").orElse(null);
        if (hand == null) return;

        poseStack.push();

        poseStack.translate(hand.getWorldPosition().x(), hand.getWorldPosition().y(), hand.getWorldPosition().z());

        poseStack.scale(0.6f, 0.6f, 0.6f);
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));

        ItemRenderer ir = MinecraftClient.getInstance().getItemRenderer();
        ir.renderItem(
                entity,
                stack,
                ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                false,
                poseStack,
                bufferSource,
                entity.getWorld(),
                packedLight,
                OverlayTexture.DEFAULT_UV,
                entity.getId()
        );

        poseStack.pop();
    }
}
