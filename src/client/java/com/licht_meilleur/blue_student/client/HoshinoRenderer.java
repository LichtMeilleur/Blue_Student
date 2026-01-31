package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.HoshinoEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class HoshinoRenderer extends StudentRenderer<HoshinoEntity> {
    public HoshinoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HoshinoModel(), 0.4f);
    }
}
