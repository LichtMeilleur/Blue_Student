package com.licht_meilleur.blue_student;

import com.licht_meilleur.blue_student.client.OnlyBedRenderer;
import com.licht_meilleur.blue_student.client.student_renderer.KisakiRenderer;
import com.licht_meilleur.blue_student.client.student_renderer.ShirokoRenderer;
import com.licht_meilleur.blue_student.client.student_renderer.HoshinoRenderer;
import com.licht_meilleur.blue_student.client.student_renderer.HinaRenderer;
import com.licht_meilleur.blue_student.client.student_renderer.AliceRenderer;

import com.licht_meilleur.blue_student.client.TabletBlockRenderer;
import com.licht_meilleur.blue_student.client.projectile.BulletRenderer;
import com.licht_meilleur.blue_student.client.screen.TabletScreen;
import com.licht_meilleur.blue_student.client.StudentScreen;
import com.licht_meilleur.blue_student.client.network.ClientPackets;
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


        BlockEntityRendererRegistry.register(BlueStudentMod.TABLET_BE, ctx -> new TabletBlockRenderer());
        BlockEntityRendererFactories.register(BlueStudentMod.ONLY_BED_BE, ctx -> new OnlyBedRenderer());

        HandledScreens.register(ModScreenHandlers.STUDENT_MENU, StudentScreen::new);

        // ★タブレットは ScreenHandler じゃなく “Clientで直接Screen開く” 方式
        BlueStudentMod.OPEN_TABLET_SCREEN = TabletScreen::open;

        EntityRendererRegistry.register(BlueStudentMod.STUDENT_BULLET, ctx -> new BulletRenderer(ctx));
        ClientPackets.registerS2C();
    }
}
