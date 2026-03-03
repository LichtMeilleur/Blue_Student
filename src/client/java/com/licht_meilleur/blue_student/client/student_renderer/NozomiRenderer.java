package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.*;
import com.licht_meilleur.blue_student.entity.*;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class NozomiRenderer extends StudentRenderer<NozomiEntity> {
    public NozomiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NozomiModel(), 0.4f);
    }
}
