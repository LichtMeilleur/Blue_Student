package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class ShirokoRenderer extends StudentRenderer<ShirokoEntity> {
    public ShirokoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ShirokoModel(), 0.4f);
    }
}
