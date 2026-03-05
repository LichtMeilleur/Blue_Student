package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.GunTrainEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GunTrainRenderer extends GeoEntityRenderer<GunTrainEntity> {

    public GunTrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GunTrainModel());
        this.shadowRadius = 0f;
    }
}