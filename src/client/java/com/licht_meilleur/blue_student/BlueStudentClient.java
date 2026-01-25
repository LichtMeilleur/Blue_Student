package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.client.ShirokoRenderer;
import com.licht_meilleur.blue_student.client.TabletBlockRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BlueStudentClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BlueStudentMod.SHIROKO, ShirokoRenderer::new);
        BlockEntityRendererRegistry.register(BlueStudentMod.TABLET_BE, ctx -> new TabletBlockRenderer());
    }
}