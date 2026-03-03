package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class TrainModel extends GeoModel<TrainEntity> {

    @Override
    public Identifier getModelResource(TrainEntity animatable) {
        return id("geo/train.geo.json");
    }

    @Override
    public Identifier getTextureResource(TrainEntity animatable) {
        return id("textures/entity/train.png");
    }

    @Override
    public Identifier getAnimationResource(TrainEntity animatable) {
        return id("animations/train.animation.json");
    }
}
