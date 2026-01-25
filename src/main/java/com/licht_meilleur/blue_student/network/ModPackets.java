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
                var world = player.getWorld(); // ★ここが必要

                if (world.getEntityById(entityId) instanceof ShirokoEntity e) {
                    // owner未設定なら初回は操作した人をownerにする（運用に合わせて）
                    if (e.getOwnerUuid() == null) {
                        e.setOwnerUuid(player.getUuid());
                    }

                    // owner以外は操作不可
                    if (!player.getUuid().equals(e.getOwnerUuid())) return;

                    e.setAiMode(mode);
                }
            });
        });
    }
}