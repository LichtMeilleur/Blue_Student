package com.licht_meilleur.blue_student.client.others;

import com.licht_meilleur.blue_student.entity.GunTrainEntity;
import com.licht_meilleur.blue_student.entity.TrainEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class GunTrainModel extends GeoModel<GunTrainEntity> {

    @Override
    public Identifier getModelResource(GunTrainEntity animatable) {
        return id("geo/gun_train.geo.json");
    }

    @Override
    public Identifier getTextureResource(GunTrainEntity animatable) {
        return id("textures/entity/gun_train.png");
    }

    @Override
    public Identifier getAnimationResource(GunTrainEntity animatable) {
        return id("animations/gun_train.animation.json");
    }
}
