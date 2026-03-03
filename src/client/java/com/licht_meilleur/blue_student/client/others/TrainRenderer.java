package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TrainRenderer extends GeoEntityRenderer<TrainEntity> {

    public TrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new TrainModel());
        this.shadowRadius = 0f; // 影いらないなら
    }
}
