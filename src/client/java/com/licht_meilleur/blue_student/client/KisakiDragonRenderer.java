package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.KisakiDragonEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KisakiDragonRenderer extends GeoEntityRenderer<KisakiDragonEntity> {

    public KisakiDragonRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new KisakiDragonModel());
        this.shadowRadius = 0f; // 影いらないなら
    }
}
