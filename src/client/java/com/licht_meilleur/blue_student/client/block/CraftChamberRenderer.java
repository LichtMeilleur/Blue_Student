package com.licht_meilleur.blue_student.client.block;

import com.licht_meilleur.blue_student.block.CraftChamberBlock;
import com.licht_meilleur.blue_student.block.entity.CraftChamberBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class CraftChamberRenderer extends GeoBlockRenderer<CraftChamberBlockEntity> {

    public CraftChamberRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new CraftChamberModel());
    }

    @Override
    public void render(CraftChamberBlockEntity be, float tickDelta,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource,
                       int packedLight, int packedOverlay) {

        poseStack.push();

        Direction dir = be.getCachedState().get(CraftChamberBlock.FACING);
        float rotY = switch (dir) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case EAST  -> -90f;
            default -> 0f;
        };

        // 中心で回転
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        poseStack.translate(-0.5, 0.0, -0.5);

        super.render(be, tickDelta, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.pop();
    }
}