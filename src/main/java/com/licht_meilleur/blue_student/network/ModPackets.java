package com.licht_meilleur.blue_student.network;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class ModPackets {
    public static final Identifier SET_AI_MODE = BlueStudentMod.id("set_ai_mode");

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(SET_AI_MODE, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            int mode = buf.readInt();

            server.execute(() -> {
                var world = player.getWorld();
                if (world.getEntityById(entityId) instanceof ShirokoEntity e) {
                    // 所有者チェック等は後で入れてOK（まず動作優先）
                    e.setAiMode(mode);
                }
            });
        });
    }
}