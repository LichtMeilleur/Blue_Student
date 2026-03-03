package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.MarieModel;
import com.licht_meilleur.blue_student.entity.MarieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class MarieRenderer extends StudentRenderer<MarieEntity> {
    public MarieRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new MarieModel(), 0.4f);
    }
}
