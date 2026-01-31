package com.licht_meilleur.blue_student.network;

import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import io.netty.buffer.Unpooled;

public class ServerFx {

    // 送信フォーマット：
    // int shooterId
    // double start(x,y,z)
    // varInt fxTypeOrd
    // float fxWidth
    // varInt n
    // n * float dir(x,y,z)
    public static void sendShotFx(ServerWorld sw,
                                  int shooterEntityId,
                                  Vec3d start,
                                  WeaponSpec.FxType fxType,
                                  float fxWidth,
                                  Vec3d[] dirs,
                                  float travelDist
    ) {

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(shooterEntityId);

        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);

        buf.writeVarInt(fxType.ordinal());
        buf.writeFloat(fxWidth);

        buf.writeFloat(travelDist);

        int n = (dirs == null) ? 0 : dirs.length;
        buf.writeVarInt(n);

        for (int i = 0; i < n; i++) {
            Vec3d d = (dirs[i] == null) ? Vec3d.ZERO : dirs[i].normalize();
            buf.writeFloat((float) d.x);
            buf.writeFloat((float) d.y);
            buf.writeFloat((float) d.z);
        }

        for (var p : PlayerLookup.world(sw)) {
            ServerPlayNetworking.send(p, com.licht_meilleur.blue_student.network.ModPackets.S2C_SHOT_FX, buf);
        }
    }

    public static void sendShotFx(ServerWorld sw, int shooterEntityId, Vec3d start,
                                  WeaponSpec.FxType fxType, float fxWidth, Vec3d dir, float travelDist) {
        sendShotFx(sw, shooterEntityId, start, fxType, fxWidth, new Vec3d[]{dir}, travelDist);
    }
}
