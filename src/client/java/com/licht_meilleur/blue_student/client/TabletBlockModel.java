package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.entity.TabletBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class TabletBlockModel extends GeoModel<TabletBlockEntity> {
    @Override
    public Identifier getModelResource(TabletBlockEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "geo/tablet.geo.json");
    }

    @Override
    public Identifier getTextureResource(TabletBlockEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "textures/entity/tablet.png");
    }

    @Override
    public Identifier getAnimationResource(TabletBlockEntity animatable) {
        return new Identifier(BlueStudentMod.MOD_ID, "animations/tablet.animation.json");
    }
}