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

import java.lang.reflect.Method;

public class StudentRenderer<T extends AbstractStudentEntity> extends GeoEntityRenderer<T> {

    public StudentRenderer(EntityRendererFactory.Context ctx, GeoModel<T> model, float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
    }

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

/*
        // 1) 旧方式：bone名が muzzle/sub_muzzle の場合（ボーン方式でも動く）
        if ("muzzle".equals(bone.getName())) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack, 0, 0, 0);
            animatable.setClientMuzzleWorldPos(w);
        }
        if ("sub_muzzle".equals(bone.getName())) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack, 0, 0, 0);
            animatable.setClientSubMuzzleWorldPos(w);
        }

*/



        // 2) 新方式：Bedrock geo の "locators" を拾う（BRがこれ）
        //    locator座標は「ピクセル単位」なので /16 してブロック単位にする
        Vec3d muzzleLocal = tryGetLocatorLocalPos(bone, "muzzle");
        if (muzzleLocal != null) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack,
                    (float) (muzzleLocal.x / 16.0),
                    (float) (muzzleLocal.y / 16.0),
                    (float) (muzzleLocal.z / 16.0)
            );
            animatable.setClientMuzzleWorldPos(w);
        }

        Vec3d subLocal = tryGetLocatorLocalPos(bone, "sub_muzzle");
        if (subLocal != null) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack,
                    (float) (subLocal.x / 16.0),
                    (float) (subLocal.y / 16.0),
                    (float) (subLocal.z / 16.0)
            );
            animatable.setClientSubMuzzleWorldPos(w);
        }

        Vec3d sub_L_Local = tryGetLocatorLocalPos(bone, "Left_sub_muzzle");
        if (sub_L_Local != null) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack,
                    (float) (sub_L_Local.x / 16.0),
                    (float) (sub_L_Local.y / 16.0),
                    (float) (sub_L_Local.z / 16.0)
            );
            animatable.setclientLeftSubMuzzleWorldPos(w);
        }

        Vec3d sub_R_Local = tryGetLocatorLocalPos(bone, "right_sub_muzzle");
        if (sub_R_Local != null) {
            Vec3d w = worldPosFromCurrentMatrix(poseStack,
                    (float) (sub_R_Local.x / 16.0),
                    (float) (sub_R_Local.y / 16.0),
                    (float) (sub_R_Local.z / 16.0)
            );
            animatable.setclientRightSubMuzzleWorldPos(w);
        }

        super.renderRecursively(poseStack, animatable, bone, renderLayer, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * 現在の poseStack 行列（=カメラ相対）にローカル座標を乗せてワールド座標に戻す
     */
    private Vec3d worldPosFromCurrentMatrix(MatrixStack poseStack, float lx, float ly, float lz) {
        Matrix4f mat = new Matrix4f(poseStack.peek().getPositionMatrix());
        Vector4f v = new Vector4f(lx, ly, lz, 1);
        v.mul(mat);

        return new Vec3d(v.x, v.y, v.z); // ← camera足さない
    }

    /**
     * GeckoLibの内部API差があるので reflection で locator を拾う。
     * 返り値は「ピクセル単位」の座標(Vec3d)想定（geoの値そのまま）
     */
    private Vec3d tryGetLocatorLocalPos(GeoBone bone, String locatorName) {
        // 1) getLocators() があるなら最優先（存在確認できる）
        try {
            Method m = bone.getClass().getMethod("getLocators");
            Object map = m.invoke(bone);
            if (map instanceof java.util.Map<?, ?> mp) {
                if (!mp.containsKey(locatorName)) return null; // ★これが肝
                Object v = mp.get(locatorName);
                if (v == null) return null;
                return new Vec3d(
                        readFieldAsDouble(v, "x"),
                        readFieldAsDouble(v, "y"),
                        readFieldAsDouble(v, "z")
                );
            }
        } catch (Throwable ignored) {}

        // 2) getLocatorPosition は最後（無いのに 0,0,0 を返す実装がある）
        try {
            Method m = bone.getClass().getMethod("getLocatorPosition", String.class);
            Object r = m.invoke(bone, locatorName);
            if (r == null) return null;

            double x = readFieldAsDouble(r, "x");
            double y = readFieldAsDouble(r, "y");
            double z = readFieldAsDouble(r, "z");

            // ★保険：完全ゼロは「無いのにゼロ返し」疑惑が強いので弾く
            if (x == 0.0 && y == 0.0 && z == 0.0) return null;

            return new Vec3d(x, y, z);
        } catch (Throwable ignored) {}

        return null;
    }

    private double readFieldAsDouble(Object obj, String field) {
        try {
            // public field
            var f = obj.getClass().getField(field);
            Object v = f.get(obj);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}

        try {
            // getter
            String mname = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            Method m = obj.getClass().getMethod(mname);
            Object v = m.invoke(obj);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}

        return 0.0;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {

        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);

        // ===== 右手アイテム表示 =====
        ItemStack stack = entity.getEatingStackForRender();
        if (stack.isEmpty()) return;

        var handOpt = this.getGeoModel().getBone("RightHandLocator");
        GeoBone hand = handOpt.orElse(null);
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