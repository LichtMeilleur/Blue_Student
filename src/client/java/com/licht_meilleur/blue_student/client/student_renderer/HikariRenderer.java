package com.licht_meilleur.blue_student.client.student_renderer;

import com.licht_meilleur.blue_student.client.StudentRenderer;
import com.licht_meilleur.blue_student.client.student_model.HikariModel;
import com.licht_meilleur.blue_student.client.student_model.MarieModel;
import com.licht_meilleur.blue_student.entity.HikariEntity;
import com.licht_meilleur.blue_student.entity.MarieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class HikariRenderer extends StudentRenderer<HikariEntity> {
    public HikariRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HikariModel(), 0.4f);
    }
}
