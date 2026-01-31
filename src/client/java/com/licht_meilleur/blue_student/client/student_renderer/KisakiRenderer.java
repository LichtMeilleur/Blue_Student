package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.KisakiModel;
import com.licht_meilleur.blue_student.entity.KisakiEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class KisakiRenderer extends StudentRenderer<KisakiEntity> {
    public KisakiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new KisakiModel(), 0.4f);
    }
}
