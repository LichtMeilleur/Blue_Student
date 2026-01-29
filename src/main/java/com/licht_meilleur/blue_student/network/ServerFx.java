package com.licht_meilleur.blue_student.network;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import io.netty.buffer.Unpooled;

public class ServerFx {
    public static final Identifier S2C_SHOT_FX = BlueStudentMod.id("s2c_shot_fx");

    public static void sendShotFx(ServerWorld sw, int shooterEntityId, Vec3d spawnPos, Vec3d dir) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(shooterEntityId);
        buf.writeDouble(spawnPos.x);
        buf.writeDouble(spawnPos.y);
        buf.writeDouble(spawnPos.z);

        // dir（正規化しておく）
        Vec3d nd = dir.normalize();
        buf.writeFloat((float) nd.x);
        buf.writeFloat((float) nd.y);
        buf.writeFloat((float) nd.z);

        for (var p : PlayerLookup.world(sw)) {
            ServerPlayNetworking.send(p, ModPackets.S2C_SHOT_FX, buf);
        }
    }
}
