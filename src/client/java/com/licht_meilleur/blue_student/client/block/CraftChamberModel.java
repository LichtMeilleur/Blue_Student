// CraftChamberModel.java
package com.licht_meilleur.blue_student.client.block;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.entity.CraftChamberBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CraftChamberModel extends GeoModel<CraftChamberBlockEntity> {
    @Override
    public Identifier getModelResource(CraftChamberBlockEntity animatable) {
        return BlueStudentMod.id("geo/craft_chamber.geo.json");
    }

    @Override
    public Identifier getTextureResource(CraftChamberBlockEntity animatable) {
        return BlueStudentMod.id("textures/block/craft_chamber.png");
    }

    @Override
    public Identifier getAnimationResource(CraftChamberBlockEntity animatable) {
        return BlueStudentMod.id("animations/craft_chamber.animation.json");
    }
}