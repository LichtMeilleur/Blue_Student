package com.licht_meilleur.blue_student.ai.only;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.MarieEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.UUID;

public class MarieBuffGoal extends Goal {

    private final AbstractStudentEntity marie;
    private final IStudentEntity student;

    private static final double RANGE = 14.0;
    private static final int CHECK_INTERVAL = 20; // 1秒

    // バフの持続（次のtickで延長する想定）
    private static final int BUFF_TTL_TICKS = 60; // 3秒

    // 付与する効果（例）
    private static final int ABSORPTION_AMP = 0; // Lv1
    private static final int REGEN_AMP      = 0; // Lv1

    private int nextCheck = 0;

    // “今バフしている相手”
    private UUID currentTargetPlayer = null;

    public MarieBuffGoal(AbstractStudentEntity marie, IStudentEntity student) {
        this.marie = marie;
        this.student = student;
        this.setControls(EnumSet.noneOf(Control.class)); // 競合ゼロ
    }

    @Override
    public boolean canStart() {
        return !marie.getWorld().isClient && !marie.isLifeLockedForGoal();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        if (marie.getWorld().isClient) return;
        if (--nextCheck > 0) return;
        nextCheck = CHECK_INTERVAL;

        if (!(marie.getWorld() instanceof ServerWorld sw)) return;

        // 1) まず「オーナーのプレイヤー」を優先ターゲットにする
        UUID owner = student.getOwnerUuid();
        ServerPlayerEntity ownerPlayer = null;
        if (owner != null) {
            ownerPlayer = sw.getServer().getPlayerManager().getPlayer(owner);
            if (!isValidTarget(ownerPlayer)) ownerPlayer = null;
        }

        // 2) 既存ターゲット維持（オーナーが有効ならそれ優先で上書き）
        ServerPlayerEntity cur = resolvePlayer(sw, currentTargetPlayer);

        // オーナーが有効なら、オーナーに固定
        if (ownerPlayer != null) {
            if (currentTargetPlayer == null || !ownerPlayer.getUuid().equals(currentTargetPlayer)) {
                currentTargetPlayer = ownerPlayer.getUuid();
                onTargetChanged(sw, ownerPlayer);
            }
            applyAndRefresh(sw, ownerPlayer);
            return;
        }

        // オーナーが居ない/範囲外 → 既存ターゲットが有効なら維持
        if (isValidTarget(cur)) {
            applyAndRefresh(sw, cur);
            return;
        }

        // 3) 既存無効 → 近くのプレイヤー（任意：オーナー不問）から最寄りを選ぶ
        ServerPlayerEntity best = findNearestPlayerInRange(sw);
        if (best != null) {
            if (currentTargetPlayer == null || !best.getUuid().equals(currentTargetPlayer)) {
                currentTargetPlayer = best.getUuid();
                onTargetChanged(sw, best);
            }
            applyAndRefresh(sw, best);
        } else {
            currentTargetPlayer = null;
        }
    }

    private boolean isValidTarget(ServerPlayerEntity p) {
        if (p == null) return false;
        if (!p.isAlive()) return false;

        double r2 = RANGE * RANGE;
        if (marie.squaredDistanceTo(p) > r2) return false;

        // 好み：見えている相手だけにしたいなら
        // if (!marie.canSee(p)) return false;

        return true;
    }

    private ServerPlayerEntity findNearestPlayerInRange(ServerWorld sw) {
        ServerPlayerEntity best = null;
        double bestD2 = 1e18;

        Box box = marie.getBoundingBox().expand(RANGE);
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (!box.contains(sp.getPos())) continue;
            if (!isValidTarget(sp)) continue;
            double d2 = marie.squaredDistanceTo(sp);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = sp;
            }
        }
        return best;
    }

    private ServerPlayerEntity resolvePlayer(ServerWorld sw, UUID uuid) {
        if (uuid == null) return null;
        return sw.getServer().getPlayerManager().getPlayer(uuid);
    }

    private void applyAndRefresh(ServerWorld sw, ServerPlayerEntity target) {
        // ★ここが「バフ本体」：例として吸収＋リジェネ
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, BUFF_TTL_TICKS, ABSORPTION_AMP, true, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, BUFF_TTL_TICKS, REGEN_AMP, true, true, true));

        // 視認用：対象の頭上（控えめ）
        spawnTargetParticles(sw, target);
    }

    private void onTargetChanged(ServerWorld sw, ServerPlayerEntity newTarget) {
        // ① マリーの祈りアニメ（あなたのトリガー）
        if (marie instanceof MarieEntity me) {
            me.requestBuff();
        }

        // ② バフ開始の派手演出：風で左に流す（マリー起点）
        spawnWindBurstFromMarie(sw);
    }

    private void spawnTargetParticles(ServerWorld sw, ServerPlayerEntity target) {
        Vec3d p = target.getPos().add(0, target.getHeight() + 0.2, 0);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                p.x, p.y, p.z,
                6,          // count
                0.25, 0.15, 0.25, // spread
                0.01        // speed
        );
    }

    /**
     * 「マリーから見て左」に風で流れる演出
     * - yawから左ベクトルを計算
     * - count=0で1粒ずつ速度を指定して流す
     */
    private void spawnWindBurstFromMarie(ServerWorld sw) {
        Vec3d origin = marie.getPos().add(0, marie.getStandingEyeHeight() - 0.1, 0);

        // yaw（度→rad）
        float yawRad = marie.getYaw() * MathHelper.RADIANS_PER_DEGREE;

        // 前方（XZ）
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));

        // 元: left = (-forward.z, 0, forward.x)
        // ★逆向きだったので反転（=右ベクトルにする）
        Vec3d driftSide = new Vec3d(-forward.z, 0, forward.x).normalize().multiply(-1.0);

        // 祈りの「光の粒」：ふわっと上昇＋左右へ流れ＋外側へ拡散
        int n = 26;                 // 粒数
        double sideSpeed = 0.16;    // 左右へ流れる強さ
        double upSpeed   = 0.07;    // 上昇
        double spreadVel = 0.10;    // 拡散（散っていく）

        for (int i = 0; i < n; i++) {
            // 粒の初期位置：originの周りに小さくばらす
            double ox = (sw.random.nextDouble() - 0.5) * 0.25;
            double oy = (sw.random.nextDouble()) * 0.25;       // 少し上側に寄せる
            double oz = (sw.random.nextDouble() - 0.5) * 0.25;

            Vec3d spawnPos = origin.add(ox, oy, oz);

            // ランダムな「外向き」方向（XZ中心、少し上下も）
            double rx = (sw.random.nextDouble() - 0.5);
            double ry = (sw.random.nextDouble() - 0.2) * 0.35; // 上方向ちょい多め
            double rz = (sw.random.nextDouble() - 0.5);
            Vec3d radial = new Vec3d(rx, ry, rz).normalize();

            // 最終速度：左右ドリフト + 上昇 + 拡散
            Vec3d vel = driftSide.multiply(sideSpeed)
                    .add(0, upSpeed, 0)
                    .add(radial.multiply(spreadVel));

            // 光粒子：END_ROD は「キラキラ」感が強く、祈りっぽくなる
            sw.spawnParticles(
                    ParticleTypes.END_ROD,
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    0,          // count=0（速度を粒ごとに指定する）
                    vel.x, vel.y, vel.z,
                    1.0
            );
        }

        // 仕上げに「淡いもや」を少し（広がって消える感じの補助）
        // ※count>0 で spread を使うと “ふわっ” が出しやすい
        sw.spawnParticles(
                ParticleTypes.ENCHANT,
                origin.x, origin.y + 0.15, origin.z,
                18,                 // count
                0.35, 0.25, 0.35,   // spread
                0.01
        );
    }
}