package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class OnlyBedRenderer extends GeoBlockRenderer<OnlyBedBlockEntity> {
    public OnlyBedRenderer() {
        super(new OnlyBedModel());
    }
}