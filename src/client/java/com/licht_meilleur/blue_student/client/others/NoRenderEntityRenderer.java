package com.licht_meilleur.blue_student.client.others;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public class NoRenderEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    // 使われないけど null だと怖いので適当なテクスチャを返す
    private static final Identifier DUMMY = new Identifier("minecraft", "textures/misc/white.png");

    public NoRenderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // 何も描画しない（クラッシュ回避用）
    }

    @Override
    public Identifier getTexture(T entity) {
        return DUMMY;
    }
}