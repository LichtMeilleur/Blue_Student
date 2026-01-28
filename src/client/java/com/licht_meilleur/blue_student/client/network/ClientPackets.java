package com.licht_meilleur.blue_student.client.network;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class ClientPackets {

    public static void registerS2C() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_SHOT_FX, (client, handler, buf, responseSender) -> {
            final int shooterId = buf.readVarInt();
            final Vec3d dir = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());

            client.execute(() -> {
                ClientWorld w = MinecraftClient.getInstance().world;
                if (w == null) return;

                Entity e = w.getEntityById(shooterId);
                if (!(e instanceof AbstractStudentEntity student)) return;

                // ★まずは近似 muzzle（確実に動く）
                Vec3d muzzle = student.getMuzzlePosApproxClient();

                // マズルフラッシュ
                for (int i = 0; i < 6; i++) {
                    double sx = muzzle.x + (w.random.nextDouble() - 0.5) * 0.05;
                    double sy = muzzle.y + (w.random.nextDouble() - 0.5) * 0.05;
                    double sz = muzzle.z + (w.random.nextDouble() - 0.5) * 0.05;
                    w.addParticle(ParticleTypes.CRIT, sx, sy, sz,
                            dir.x * 0.08, dir.y * 0.08, dir.z * 0.08);
                }

                // 簡易トレーサー
                Vec3d p = muzzle;
                for (int i = 0; i < 8; i++) {
                    p = p.add(dir.multiply(0.6));
                    w.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0, 0);
                }

                // 音（仮）
                w.playSound(muzzle.x, muzzle.y, muzzle.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                        0.15f, 1.6f, false);
            });
        });
    }
}
