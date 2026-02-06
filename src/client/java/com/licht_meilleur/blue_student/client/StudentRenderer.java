package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class StudentRenderer<T extends AbstractStudentEntity> extends GeoEntityRenderer<T> {

    public StudentRenderer(EntityRendererFactory.Context ctx, GeoModel<T> model, float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
    }

    /**
     * ★ここで muzzle ボーンの “描画中の行列” からワールド座標を取り出す
     *  - GeckoLibのbone階層/回転/スケールが全部反映される
     *  - いまの getWorldPosition 足し算方式より圧倒的にズレにくい
     */
    @Override
    public void renderRecursively(MatrixStack poseStack,
                                  T animatable,
                                  GeoBone bone,
                                  RenderLayer renderLayer,
                                  VertexConsumerProvider bufferSource,
                                  VertexConsumer buffer,
                                  boolean isReRender,
                                  float partialTick,
                                  int packedLight,
                                  int packedOverlay,
                                  float red, float green, float blue, float alpha) {

        // muzzle の “今の描画姿勢” からワールド座標を取り出す
        if ("muzzle".equals(bone.getName())) {
            Matrix4f mat = new Matrix4f(poseStack.peek().getPositionMatrix());
            Vector4f v = new Vector4f(0, 0, 0, 1);
            v.mul(mat);

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Vec3d cam = mc.gameRenderer.getCamera().getPos();
                animatable.setClientMuzzleWorldPos(new Vec3d(v.x + cam.x, v.y + cam.y, v.z + cam.z));
            }
        }

        super.renderRecursively(poseStack, animatable, bone, renderLayer, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {

        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);

        // ===== 右手アイテム表示 =====
        ItemStack stack = entity.getEatingStackForRender();
        if (stack.isEmpty()) return;

        // 右手ロケータ名はあなたのモデルに合わせる
        GeoBone hand = this.getGeoModel().getBone("RightHandLocator").orElse(null);
        if (hand == null) return;

        poseStack.push();

        // hand も “描画行列で取る” のが本当は理想だけど、
        // まずは現状維持でOK（必要ならこちらも renderRecursively方式にできます）
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
