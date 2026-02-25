package com.licht_meilleur.blue_student.client.projectile;

import com.licht_meilleur.blue_student.entity.projectile.SonicBeamEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class SonicBeamRenderer extends EntityRenderer<SonicBeamEntity> {

    public SonicBeamRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SonicBeamEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {

        Vec3d start = entity.getStart();
        Vec3d end = entity.getEnd();

        Vec3d cam = this.dispatcher.camera.getPos();

        matrices.push();
        matrices.translate(start.x - cam.x, start.y - cam.y, start.z - cam.z);

        Vec3d dir = end.subtract(start);

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLightning());

        drawBeam(buffer, matrices.peek().getPositionMatrix(), dir, 0.3f);

        matrices.pop();
    }

    private void drawBeam(VertexConsumer buffer, Matrix4f matrix, Vec3d dir, float width) {

        float x = (float) dir.x;
        float y = (float) dir.y;
        float z = (float) dir.z;

        buffer.vertex(matrix, -width, 0, 0).color(80, 200, 255, 180).next();
        buffer.vertex(matrix, width, 0, 0).color(80, 200, 255, 180).next();
        buffer.vertex(matrix, width, y, z).color(80, 200, 255, 180).next();
        buffer.vertex(matrix, -width, y, z).color(80, 200, 255, 180).next();
    }

    @Override
    public Identifier getTexture(SonicBeamEntity entity) {
        return null;
    }
}