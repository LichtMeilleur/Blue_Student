package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.AliceModel;
import com.licht_meilleur.blue_student.entity.AliceEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class AliceRenderer extends StudentRenderer<AliceEntity> {
    public AliceRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AliceModel(), 0.4f);
    }
}
