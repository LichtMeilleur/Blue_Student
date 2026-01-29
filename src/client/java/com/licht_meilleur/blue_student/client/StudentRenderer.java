package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class StudentRenderer<T extends AbstractStudentEntity> extends GeoEntityRenderer<T> {

    public StudentRenderer(EntityRendererFactory.Context ctx, GeoModel<T> model, float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {

        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);

        // muzzleのワールド座標をEntityにキャッシュ
        GeoBone muzzle = this.getGeoModel().getBone("muzzle").orElse(null);
        if (muzzle == null) return;

        // GeckoLibのバージョン差でここがズレる可能性があるので、
        // もしコンパイルエラー出たらそのエラー文を貼ってください（すぐ合わせます）
        Vec3d wp = new Vec3d(
                entity.getX() + muzzle.getWorldPosition().x(),
                entity.getY() + muzzle.getWorldPosition().y(),
                entity.getZ() + muzzle.getWorldPosition().z()
        );

        entity.setClientMuzzleWorldPos(wp);
    }
}
