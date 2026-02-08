package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.KisakiDragonEntity;
import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShirokoDroneRenderer extends GeoEntityRenderer<ShirokoDroneEntity> {

    public ShirokoDroneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ShirokoDroneModel());
        this.shadowRadius = 0f; // 影いらないなら
    }
}
