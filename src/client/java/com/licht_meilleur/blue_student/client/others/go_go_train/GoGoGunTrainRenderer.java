package com.licht_meilleur.blue_student.client.others.go_go_train;

import com.licht_meilleur.blue_student.entity.go_go_train.GoGoGunTrainEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GoGoGunTrainRenderer extends GeoEntityRenderer<GoGoGunTrainEntity> {
    public GoGoGunTrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GoGoGunTrainModel());
        this.shadowRadius = 0f;
    }
}