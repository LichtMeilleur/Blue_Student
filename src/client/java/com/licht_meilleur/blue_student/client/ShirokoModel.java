package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.ShirokoEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ShirokoModel extends GeoModel<ShirokoEntity> {

    @Override
    public Identifier getModelResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "geo/shiroko.geo.json");
    }

    @Override
    public Identifier getTextureResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "textures/entity/shiroko.png");
    }

    @Override
    public Identifier getAnimationResource(ShirokoEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "animations/shiroko.animation.json");
    }
}