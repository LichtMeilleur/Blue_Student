package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.block.entity.TabletBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TabletBlockRenderer extends GeoBlockRenderer<TabletBlockEntity> {
    public TabletBlockRenderer() {
        super(new TabletBlockModel());
    }
}