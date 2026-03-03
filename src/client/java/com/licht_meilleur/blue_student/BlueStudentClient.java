package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.client.block.CraftChamberRenderer;
import com.licht_meilleur.blue_student.client.block.OnlyBedRenderer;
import com.licht_meilleur.blue_student.client.block.TabletBlockRenderer;
import com.licht_meilleur.blue_student.client.others.KisakiDragonRenderer;
import com.licht_meilleur.blue_student.client.others.ShirokoDroneRenderer;
import com.licht_meilleur.blue_student.client.screen.StudentScreen;
import com.licht_meilleur.blue_student.client.student_renderer.*;
import com.licht_meilleur.blue_student.client.others.*;


import com.licht_meilleur.blue_student.client.projectile.SonicBeamRenderer;
import com.licht_meilleur.blue_student.client.projectile.BulletRenderer;
import com.licht_meilleur.blue_student.client.screen.TabletScreen;
import com.licht_meilleur.blue_student.client.screen.CraftChamberScreen;
import com.licht_meilleur.blue_student.client.network.ClientPackets;
import com.licht_meilleur.blue_student.registry.ModEntities;
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

        // ★順番が前後しても大丈夫なように、クライアント側でも必ず登録（ガード付き）
        ModScreenHandlers.register();

        EntityRendererRegistry.register(BlueStudentMod.SHIROKO, ShirokoRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.HOSHINO, HoshinoRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.HINA, HinaRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.KISAKI, KisakiRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.ALICE, AliceRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.MARIE, MarieRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.HIKARI, HikariRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.NOZOMI, NozomiRenderer::new);




        EntityRendererRegistry.register(BlueStudentMod.KISAKI_DRAGON, KisakiDragonRenderer::new);
        EntityRendererRegistry.register(BlueStudentMod.SHIROKO_DRONE, ShirokoDroneRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRAIN, TrainRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUN_TRAIN, GunTrainRenderer::new);

        BlockEntityRendererRegistry.register(BlueStudentMod.TABLET_BE, ctx -> new TabletBlockRenderer());
        BlockEntityRendererRegistry.register(BlueStudentMod.CRAFT_CHAMBER_BE, ctx -> new CraftChamberRenderer(ctx));
        BlockEntityRendererFactories.register(BlueStudentMod.ONLY_BED_BE, ctx -> new OnlyBedRenderer());

        HandledScreens.register(ModScreenHandlers.STUDENT_MENU, StudentScreen::new);
        HandledScreens.register(ModScreenHandlers.CRAFT_CHAMBER_MENU, CraftChamberScreen::new);

        // ★タブレットは ScreenHandler じゃなく “Clientで直接Screen開く” 方式
        BlueStudentMod.OPEN_TABLET_SCREEN = TabletScreen::open;

        EntityRendererRegistry.register(BlueStudentMod.STUDENT_BULLET, ctx -> new BulletRenderer(ctx));
        //EntityRendererRegistry.register(ModEntities.HYPER_CANNON, HyperCannonRenderer::new);
        EntityRendererRegistry.register(ModEntities.SONIC_BEAM, SonicBeamRenderer::new);
        ClientPackets.registerS2C();
    }
}
