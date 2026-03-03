package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.entity.CraftChamberBlockEntity;
import com.licht_meilleur.blue_student.inventory.CraftChamberScreenHandler;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
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
    public static ScreenHandlerType<CraftChamberScreenHandler> CRAFT_CHAMBER_MENU;

    private static boolean REGISTERED = false;

    public static void register() {
        if (REGISTERED) return;
        REGISTERED = true;

        STUDENT_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("student_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::createStudent)
        );

        CRAFT_CHAMBER_MENU = Registry.register(
                Registries.SCREEN_HANDLER,
                BlueStudentMod.id("craft_chamber_menu"),
                new ExtendedScreenHandlerType<>(ModScreenHandlers::createCraftChamber)
        );
    }

    private static StudentScreenHandler createStudent(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        int entityId = buf.readInt();
        var world = inv.player.getWorld();

        IStudentEntity e = null;
        var raw = world.getEntityById(entityId);
        if (raw instanceof IStudentEntity se) e = se;

        return new StudentScreenHandler(syncId, inv, e, new SimpleInventory(9), new SimpleInventory(1));
    }

    private static CraftChamberScreenHandler createCraftChamber(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        var world = inv.player.getWorld();

        CraftChamberBlockEntity be = null;
        var raw = world.getBlockEntity(pos);
        if (raw instanceof CraftChamberBlockEntity cc) be = cc;

        return new CraftChamberScreenHandler(syncId, inv, be, pos);
    }
}