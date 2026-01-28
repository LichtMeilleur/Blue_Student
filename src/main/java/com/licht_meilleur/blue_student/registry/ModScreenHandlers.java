package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public class ModScreenHandlers {

    public static ScreenHandlerType<StudentScreenHandler> STUDENT_MENU;

    private static boolean REGISTERED = false;

    public static void register() {
        if (REGISTERED) return;         // ★二重登録防止
        REGISTERED = true;

        STUDENT_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("student_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::createStudent)
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
}
