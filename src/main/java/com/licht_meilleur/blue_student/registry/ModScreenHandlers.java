package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.inventory.TabletScreenHandler;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public class ModScreenHandlers {

    public static ScreenHandlerType<StudentScreenHandler> STUDENT_MENU;
    public static ScreenHandlerType<TabletScreenHandler>  TABLET_MENU;

    public static void register() {

        // ===== Student (entityId を buf で受け取る) =====
        STUDENT_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("student_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::createStudent)
        );

        // ===== Tablet (blockPos を buf で受け取る) =====
        TABLET_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("tablet_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::createTablet)
        );
    }

    private static StudentScreenHandler createStudent(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        int entityId = buf.readInt();
        var world = inv.player.getWorld();

        IStudentEntity e = null;
        var raw = world.getEntityById(entityId);
        if (raw instanceof IStudentEntity se) e = se;

        // クライアント側は “ダミー9スロ” でOK
        return new StudentScreenHandler(syncId, inv, e, new SimpleInventory(9));
    }

    private static TabletScreenHandler createTablet(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        return new TabletScreenHandler(syncId, inv, buf);
    }
}
