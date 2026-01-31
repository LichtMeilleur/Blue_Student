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
                        spawnRailShot(w, start, dirs[0], travelDist);
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

    // ★★★ ここに追加 ★★★
    private static void spawnRailBeam(ClientWorld w, Vec3d start, Vec3d dir, float width) {
        Vec3d d = dir.normalize();

        int steps = 18; // ビームの長さ感（好みで）
        for (int i = 0; i < steps; i++) {

            double t = i / (double)steps;
            Vec3d p = start.add(d.multiply(1.2 + t * 18.0));

            double s = Math.max(0.02, width * 0.03);

            double ox = (w.random.nextDouble() - 0.5) * s;
            double oy = (w.random.nextDouble() - 0.5) * s;
            double oz = (w.random.nextDouble() - 0.5) * s;

            // 芯
            w.addParticle(ParticleTypes.END_ROD, true, p.x + ox, p.y + oy, p.z + oz, 0, 0, 0);

            // 火花
            if (i % 3 == 0)
                w.addParticle(ParticleTypes.CRIT, true, p.x, p.y, p.z, 0, 0, 0);




        }


    }

    private static final DustParticleEffect BLUE =
            new DustParticleEffect(new Vector3f(0.2f, 0.6f, 1.0f), 1.6f);

    private static void spawnRailOrb(ClientWorld w, Vec3d start, Vec3d dir, float travelDist) {
        Vec3d d = dir.normalize();

        // 速度（見た目だけ）：1tickあたり何m進むか
        double speed = 1.2; // 好みで調整（大きいほど速い）
        int steps = Math.max(1, (int)Math.ceil(travelDist / speed));

        Vec3d pos = start;

        for (int i = 0; i < steps; i++) {
            pos = pos.add(d.multiply(speed));

            // ★「塊」感：同じ位置に複数粒子を重ねる
            for (int k = 0; k < 10; k++) {
                double ox = (w.random.nextDouble() - 0.5) * 0.12;
                double oy = (w.random.nextDouble() - 0.5) * 0.12;
                double oz = (w.random.nextDouble() - 0.5) * 0.12;
                w.addParticle(BLUE, true, pos.x + ox, pos.y + oy, pos.z + oz, 0, 0, 0);
            }

            // 芯の光（ちょい派手）
            w.addParticle(ParticleTypes.END_ROD, true, pos.x, pos.y, pos.z, 0, 0, 0);
        }

        // 着弾っぽいフラッシュ（任意）
        for (int j = 0; j < 15; j++) {
            double ox = (w.random.nextDouble() - 0.5) * 0.3;
            double oy = (w.random.nextDouble() - 0.5) * 0.3;
            double oz = (w.random.nextDouble() - 0.5) * 0.3;
            w.addParticle(BLUE, true, pos.x + ox, pos.y + oy, pos.z + oz, 0, 0, 0);
        }
    }

    private static void spawnRailShot(ClientWorld w, Vec3d start, Vec3d dir, float width, float maxDist) {
        Vec3d d = dir.normalize();

        // rightベクトル（dir × up）
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = d.crossProduct(up);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0); // 真上撃ち保険
        right = right.normalize();

        // 3本のオフセット（中央＋左右）
        double sep = 0.12 * Math.max(0.5, width); // 間隔：好みで調整
        Vec3d[] offsets = new Vec3d[] {
                right.multiply(-sep),
                Vec3d.ZERO,
                right.multiply(+sep)
        };

        // “塊”の速度：速すぎると線に見えるので少し落とす
        double speed = 0.9; // 好み
        Vec3d v = d.multiply(speed);

        // 粒子数：多いほど塊になる
        int count = 24;

        for (Vec3d off : offsets) {
            Vec3d p = start.add(off);

            for (int i = 0; i < count; i++) {
                double jx = (w.random.nextDouble() - 0.5) * 0.06;
                double jy = (w.random.nextDouble() - 0.5) * 0.06;
                double jz = (w.random.nextDouble() - 0.5) * 0.06;

                // 青い炎（メイン）
                w.addParticle(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, true,
                        p.x + jx, p.y + jy, p.z + jz,
                        v.x, v.y, v.z);

                // 光点（混ぜると“塊感”が増える）
                if (i % 3 == 0) {
                    w.addParticle(net.minecraft.particle.ParticleTypes.END_ROD, true,
                            p.x + jx, p.y + jy, p.z + jz,
                            v.x * 0.6, v.y * 0.6, v.z * 0.6);
                }
            }
        }

        // 任意：発射音
        w.playSound(start.x, start.y, start.z,
                net.minecraft.sound.SoundEvents.ENTITY_GUARDIAN_ATTACK,
                net.minecraft.sound.SoundCategory.PLAYERS,
                0.6f, 1.2f, false);
    }



}