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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

public class ClientPackets {

    public static void registerS2C() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.S2C_SHOT_FX, (client, handler, buf, responseSender) -> {

            final int shooterId = buf.readInt();
            final Vec3d spawnPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            final Vec3d dir = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());


            client.execute(() -> {
                ClientWorld w = MinecraftClient.getInstance().world;
                if (w == null) return;

                // entity取れなくても “spawnPosだけで描画できる” のが強い
                Vec3d pos = spawnPos;

                // muzzle flash
                for (int i = 0; i < 6; i++) {
                    double sx = pos.x + (w.random.nextDouble() - 0.5) * 0.05;
                    double sy = pos.y + (w.random.nextDouble() - 0.5) * 0.05;
                    double sz = pos.z + (w.random.nextDouble() - 0.5) * 0.05;
                    w.addParticle(ParticleTypes.FLAME, sx, sy, sz, 0, 0, 0);
                }

                spawnBulletTracer(w, pos, dir);
            });
        });
    }
    //private static final DustParticleEffect GOLD =
    //new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.15f), 0.8f); // サイズ小さめ推奨

    private static void spawnBulletTracer(ClientWorld w, Vec3d start, Vec3d dir) {
        Vec3d d = dir.normalize();

        for (int i = 0; i < 4; i++) { // ← 少なめ！！
            Vec3d p = start.add(d.multiply(i * 0.4));

            w.addParticle(
                    ParticleTypes.CRIT,
                    p.x, p.y, p.z,
                    d.x * 0.1, d.y * 0.1, d.z * 0.1
            );
        }
    }

}