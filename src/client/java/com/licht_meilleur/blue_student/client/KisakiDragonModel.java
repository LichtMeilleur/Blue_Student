package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.KisakiDragonEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class KisakiDragonModel extends GeoModel<KisakiDragonEntity> {

    @Override
    public Identifier getModelResource(KisakiDragonEntity animatable) {
        return id("geo/kisaki_dragon.geo.json");
    }

    @Override
    public Identifier getTextureResource(KisakiDragonEntity animatable) {
        return id("textures/entity/kisaki_dragon.png");
    }

    @Override
    public Identifier getAnimationResource(KisakiDragonEntity animatable) {
        return id("animations/kisaki_dragon.animation.json");
    }
}
