package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.projectile.GunTrainShellEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class GunTrainShellRenderer extends EntityRenderer<GunTrainShellEntity> {

    private final ItemRenderer itemRenderer;
    private static final ItemStack STACK = new ItemStack(Items.FIREWORK_ROCKET);

    public GunTrainShellRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(GunTrainShellEntity e, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        matrices.push();

        // 位置はエンティティに任せる（rendererは回転/描画のみ）
        float yaw = e.getYaw(tickDelta);
        float pitch = e.getPitch(tickDelta);

        // yaw回転
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        // pitch回転
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        // ★“上向き板”を“横向きミサイル”に寝かせる（ここが肝）
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));

        // サイズ感（好み）
        matrices.scale(1.0f, 1.0f, 1.0f);

        itemRenderer.renderItem(
                STACK,
                ModelTransformationMode.FIXED,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                e.getWorld(),
                e.getId()
        );

        matrices.pop();

        super.render(e, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(GunTrainShellEntity entity) {
        return null; // ItemRendererを使うので不要
    }
}