package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.entity.KisakiDragonEntity;
import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import static com.licht_meilleur.blue_student.BlueStudentMod.id;

public class ShirokoDroneModel extends GeoModel<ShirokoDroneEntity> {

    @Override
    public Identifier getModelResource(ShirokoDroneEntity animatable) {
        return id("geo/shiroko_drone.geo.json");
    }

    @Override
    public Identifier getTextureResource(ShirokoDroneEntity animatable) {
        return id("textures/entity/shiroko.png");
    }

    @Override
    public Identifier getAnimationResource(ShirokoDroneEntity animatable) {
        return id("animations/shiroko_drone.animation.json");
    }
}
