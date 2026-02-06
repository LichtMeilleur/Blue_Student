package com.licht_meilleur.blue_student.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class DebugChat {
    private DebugChat() {}

    // 半径内のプレイヤーにだけ送る（スパム対策）
    public static void near(Entity e, double radius, String msg) {
        if (!(e.getWorld() instanceof ServerWorld sw)) return;

        double r2 = radius * radius;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(e) <= r2) {
                p.sendMessage(Text.literal(msg), false); // false = チャット欄、true = アクションバー
            }
        }
    }
}
