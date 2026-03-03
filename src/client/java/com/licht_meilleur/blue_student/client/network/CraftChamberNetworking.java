package com.licht_meilleur.blue_student.client.network;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.Unpooled;

public final class CraftChamberNetworking {
    public static final Identifier CRAFT_REQ = BlueStudentMod.id("craft_chamber_craft");

    private CraftChamberNetworking() {}

    public static void sendCraftRequest(BlockPos pos, int pageIndex) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(pageIndex);
        ClientPlayNetworking.send(CRAFT_REQ, buf);
    }
}