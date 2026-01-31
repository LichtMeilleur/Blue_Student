package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.HinaModel;
import com.licht_meilleur.blue_student.entity.HinaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class HinaRenderer extends StudentRenderer<HinaEntity> {
    public HinaRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HinaModel(), 0.4f);
    }
}
