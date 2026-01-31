package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.ShirokoModel;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class ShirokoRenderer extends StudentRenderer<ShirokoEntity> {
    public ShirokoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ShirokoModel(), 0.4f);
    }
}
