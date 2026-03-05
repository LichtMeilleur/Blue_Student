package com.licht_meilleur.blue_student.client.others.go_go_train;

import com.licht_meilleur.blue_student.entity.go_go_train.GoGoTrainEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class GoGoTrainModel extends GeoModel<GoGoTrainEntity> {

    @Override
    public Identifier getModelResource(GoGoTrainEntity animatable) {
        return id("geo/train.geo.json");
    }

    @Override
    public Identifier getTextureResource(GoGoTrainEntity animatable) {
        return id("textures/entity/train.png");
    }

    @Override
    public Identifier getAnimationResource(GoGoTrainEntity animatable) {
        return id("animations/train.animation.json");
    }


}