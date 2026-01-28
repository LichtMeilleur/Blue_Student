
package com.licht_meilleur.blue_student.client.projectile;

import com.licht_meilleur.blue_student.entity.projectile.StudentBulletEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class BulletRenderer extends EntityRenderer<StudentBulletEntity> {
    public BulletRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(StudentBulletEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // 何も描かない
    }

    @Override
    public Identifier getTexture(StudentBulletEntity entity) { return null; }
}
