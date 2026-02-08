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
                Vec3d start2 = start;

// ★ shooter が AbstractStudentEntity なら muzzle ボーン位置を優先（見た目だけ）
                Entity shooter = w.getEntityById(shooterId);
                if (shooter instanceof AbstractStudentEntity se) {
                    start2 = se.getClientMuzzleWorldPosOrApprox();
                }


                WeaponSpec.FxType fxType = WeaponSpec.FxType.values()[fxTypeOrd];

                // muzzle flash
                for (int i = 0; i < 6; i++) {
                    double sx = start2.x + (w.random.nextDouble() - 0.5) * 0.05;
                    double sy = start2.y + (w.random.nextDouble() - 0.5) * 0.05;
                    double sz = start2.z + (w.random.nextDouble() - 0.5) * 0.05;
                    w.addParticle(ParticleTypes.FLAME, sx, sy, sz, 0, 0, 0);
                }

                switch (fxType) {
                    case BULLET -> {
                        if (dirs.length > 0) spawnOneTracer(w, start2, dirs[0]);
                    }
                    case SHOTGUN -> {
                        for (Vec3d d : dirs) spawnOneTracer(w, start2, d);
                    }
                    case RAILGUN -> {
                        spawnRailShot(w, start2, dirs[0], fxWidth, travelDist);
                    }
                    case RAILGUN_HYPER -> {
                        spawnHyperRailShot(w, start2, dirs[0], fxWidth * 1.4f, travelDist);
                    }


                }

            });
        });
    }
    private static void spawnOneTracer(ClientWorld w, Vec3d start2, Vec3d dir) {
        Vec3d d = dir.normalize();
        Vec3d v = d.multiply(3.2);

        // 芯（光る）
        w.addParticle(ParticleTypes.END_ROD, true, start2.x, start2.y, start2.z, v.x, v.y, v.z);

        // 火花（見えやすい）
        w.addParticle(ParticleTypes.CRIT, true, start2.x, start2.y, start2.z, v.x * 0.6, v.y * 0.6, v.z * 0.6);

        // ちょい煙（任意）
        w.addParticle(ParticleTypes.SMOKE, true, start2.x, start2.y, start2.z, v.x * 0.15, v.y * 0.15, v.z * 0.15);
    }



    private static final DustParticleEffect BLUE =
            new DustParticleEffect(new Vector3f(0.2f, 0.6f, 1.0f), 1.6f);



    private static void spawnRailShot(ClientWorld w, Vec3d start2, Vec3d dir, float fxWidth, float travelDist) {

        Vec3d d = dir.normalize();

        // ===== 設定 =====
        int count = 6;          // 3発
        double gap = 0.2;      // ← 玉の間隔（好みで 0.3〜0.6）
        double speed = 4.0;     // 飛ぶ速さ
        double size = 0.55 * Math.max(0.6, fxWidth);

        for (int i = 0; i < count; i++) {

            // ★進行方向に並べる（ここが今回のポイント）
            Vec3d pos = start2.add(d.multiply(gap * i));

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
                start2.x, start2.y, start2.z,
                SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS,
                0.9f,
                0.7f,
                false
        );
    }
    private static void spawnHyperRailShot(ClientWorld w, Vec3d start, Vec3d dir, float fxWidth, float travelDist) {
        Vec3d d = dir.normalize();

        // ★3つ並べる（先頭・中・後ろ）
        int blobs = 5;
        double blobGap = 0.45; // ← 塊同士の間隔（0.18〜0.45で調整）

        for (int i = 0; i < blobs; i++) {
            Vec3d s = start.add(d.multiply(blobGap * i));
            spawnHyperRailShotBlob(w, s, d, fxWidth, travelDist);
        }
        // =========================
        // ① マズル煙（供養モクモク）
        // =========================
        for (int i = 0; i < 18; i++) {
            double ox = (w.random.nextDouble() - 0.5) * 0.2;
            double oz = (w.random.nextDouble() - 0.5) * 0.2;

            w.addParticle(
                    ParticleTypes.LARGE_SMOKE,
                    true,
                    start.x + ox,
                    start.y + 0.05,
                    start.z + oz,
                    0,
                    0.08,
                    0
            );
        }
    }

    private static void spawnHyperRailShotBlob(ClientWorld w, Vec3d start, Vec3d d, float fxWidth, float travelDist) {
        double speed = 2.8;
        double size = 0.8 + fxWidth * 0.8;

        Vec3d vel = d.multiply(speed);

        // =========================
        // ① マズル煙（供養モクモク）
        // ※ 3連だと煙が3倍出てうるさいので、ここは“弾”から外すのがコツ
        // =========================
        // ここは呼び出し側（発射1回）で別に出すのがおすすめ
        //（このBlob関数からは削除推奨）

        // =========================
        // ② コア（塊）
        // =========================
        for (int i = 0; i < 5; i++) {
            w.addParticle(ParticleTypes.END_ROD, true,
                    start.x, start.y, start.z,
                    vel.x, vel.y, vel.z
            );
        }

        // =========================
        // ③ 青い外殻
        // =========================
        // ③ 青いエネルギー外殻（中心集中・球状）
        int shellCount = 80; // ←増やすほど太く見える
        double radius = 0.05 + 0.18 * fxWidth; // ←中心寄せはここ（小さいほど締まる）

        for (int i = 0; i < shellCount; i++) {
            // ランダム方向（球）
            double x = w.random.nextDouble() * 2 - 1;
            double y = w.random.nextDouble() * 2 - 1;
            double z = w.random.nextDouble() * 2 - 1;
            Vec3d off = new Vec3d(x, y, z);
            if (off.lengthSquared() < 1e-6) continue;
            off = off.normalize().multiply(radius * (0.3 + w.random.nextDouble() * 0.7)); // 内側寄せ

            w.addParticle(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    true,
                    start.x + off.x,
                    start.y + off.y,
                    start.z + off.z,
                    vel.x, vel.y, vel.z
            );
        }


        // ※着弾爆ぜ（④）は “弾3つ分” 出すと派手すぎるので
        // spawnHyperRailShot() の外で1回だけ出すのが基本です。
    }




}