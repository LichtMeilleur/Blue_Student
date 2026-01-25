package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public class ModScreenHandlers {
    public static ScreenHandlerType<StudentScreenHandler> STUDENT_MENU;

    public static void register() {
        STUDENT_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("student_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::create)
        );
    }


    private static StudentScreenHandler create(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        int entityId = buf.readInt();
        var world = inv.player.getWorld();

        ShirokoEntity e = null;
        if (world.getEntityById(entityId) instanceof ShirokoEntity se) {
            e = se;
        }

        // クライアント側は必ず SimpleInventory(9)
        return new StudentScreenHandler(syncId, inv, e, new SimpleInventory(9));
    }
}