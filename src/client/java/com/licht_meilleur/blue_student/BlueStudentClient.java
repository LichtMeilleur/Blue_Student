package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.client.*;
import com.licht_meilleur.blue_student.client.screen.TabletStudentScreen;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class BlueStudentClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[BlueStudent] onInitializeClient PRINT");

        EntityRendererRegistry.register(BlueStudentMod.SHIROKO, ShirokoRenderer::new);

        BlockEntityRendererRegistry.register(BlueStudentMod.TABLET_BE, ctx -> new TabletBlockRenderer());
        BlockEntityRendererFactories.register(BlueStudentMod.ONLY_BED_BE, ctx -> new OnlyBedRenderer());

        HandledScreens.register(ModScreenHandlers.STUDENT_MENU, StudentScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET_MENU,  TabletStudentScreen::new);
    }
}
