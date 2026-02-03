package com.licht_meilleur.blue_student.client.network;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
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
            final Vec3d start = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());

            final int fxTypeOrd = buf.readVarInt();
            final float fxWidth = buf.readFloat();

            float travelDist = buf.readFloat();

            final int n = buf.readVarInt();
            final Vec3d[] dirs = new Vec3d[n];


            for (int i = 0; i < n; i++) {
                dirs[i] = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
            }

            client.execute(() -> {
                ClientWorld w = MinecraftClient.getInstance().world;
                if (w == null) return;

                WeaponSpec.FxType fxType = WeaponSpec.FxType.values()[fxTypeOrd];

                // muzzle flash
                for (int i = 0; i < 6; i++) {
                    double sx = start.x + (w.random.nextDouble() - 0.5) * 0.05;
                    double sy = start.y + (w.random.nextDouble() - 0.5) * 0.05;
                    double sz = start.z + (w.random.nextDouble() - 0.5) * 0.05;
                    w.addParticle(ParticleTypes.FLAME, sx, sy, sz, 0, 0, 0);
                }

                switch (fxType) {
                    case BULLET -> {
                        if (dirs.length > 0) spawnOneTracer(w, start, dirs[0]);
                    }
                    case SHOTGUN -> {
                        for (Vec3d d : dirs) spawnOneTracer(w, start, d);
                    }
                    case RAILGUN -> {
                        spawnRailShot(w, start, dirs[0], fxWidth, travelDist);
                    }


                }

            });
        });
    }
    private static void spawnOneTracer(ClientWorld w, Vec3d start, Vec3d dir) {
        Vec3d d = dir.normalize();
        Vec3d v = d.multiply(3.2);

        // 芯（光る）
        w.addParticle(ParticleTypes.END_ROD, true, start.x, start.y, start.z, v.x, v.y, v.z);

        // 火花（見えやすい）
        w.addParticle(ParticleTypes.CRIT, true, start.x, start.y, start.z, v.x * 0.6, v.y * 0.6, v.z * 0.6);

        // ちょい煙（任意）
        w.addParticle(ParticleTypes.SMOKE, true, start.x, start.y, start.z, v.x * 0.15, v.y * 0.15, v.z * 0.15);
    }



    private static final DustParticleEffect BLUE =
            new DustParticleEffect(new Vector3f(0.2f, 0.6f, 1.0f), 1.6f);



    private static void spawnRailShot(ClientWorld w, Vec3d start, Vec3d dir, float fxWidth, float travelDist) {

        Vec3d d = dir.normalize();

        // ===== 設定 =====
        int count = 6;          // 3発
        double gap = 0.2;      // ← 玉の間隔（好みで 0.3〜0.6）
        double speed = 2.0;     // 飛ぶ速さ
        double size = 0.55 * Math.max(0.6, fxWidth);

        for (int i = 0; i < count; i++) {

            // ★進行方向に並べる（ここが今回のポイント）
            Vec3d pos = start.add(d.multiply(gap * i));

            // 青い炎の塊
            w.addParticle(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    true,
                    pos.x, pos.y, pos.z,
                    d.x * speed,
                    d.y * speed,
                    d.z * speed
            );

            // 中心の光（コア）
            w.addParticle(
                    ParticleTypes.END_ROD,
                    true,
                    pos.x, pos.y, pos.z,
                    d.x * speed,
                    d.y * speed,
                    d.z * speed
            );
        }

        // 発射音
        w.playSound(
                start.x, start.y, start.z,
                SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS,
                0.9f,
                0.7f,
                false
        );
    }




}