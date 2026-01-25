package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.ShirokoEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShirokoRenderer extends GeoEntityRenderer<ShirokoEntity> {
    public ShirokoRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new ShirokoModel());
        this.shadowRadius = 0.4f;
    }
}