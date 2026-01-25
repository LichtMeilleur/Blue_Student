package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.client.ShirokoRenderer;
import com.licht_meilleur.blue_student.client.TabletBlockRenderer;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import com.licht_meilleur.blue_student.client.StudentScreen;
import com.licht_meilleur.blue_student.client.OnlyBedRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererFactories;

public class BlueStudentClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BlueStudentMod.SHIROKO, ShirokoRenderer::new);
        BlockEntityRendererRegistry.register(BlueStudentMod.TABLET_BE, ctx -> new TabletBlockRenderer());
        HandledScreens.register(ModScreenHandlers.STUDENT_MENU, StudentScreen::new);
        BlockEntityRendererFactories.register(BlueStudentMod.ONLY_BED_BE, ctx -> new OnlyBedRenderer());
    }
}