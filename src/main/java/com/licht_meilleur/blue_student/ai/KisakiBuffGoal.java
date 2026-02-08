package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import com.licht_meilleur.blue_student.entity.KisakiEntity;
import net.minecraft.util.math.Vec3d;


import java.util.EnumSet;
import java.util.UUID;

public class KisakiBuffGoal extends Goal {

    private final AbstractStudentEntity kisaki;
    private final IStudentEntity student;

    private static final double RANGE = 14.0;
    private static final int CHECK_INTERVAL = 20; // 1秒

    // “シールド寄り”＝MaxHP増加 + 少し回復（安定）
    private static final double ADD_MAX_HP = 4.0;     // +2ハート
    private static final double ADD_ARMOR  = 0.0;     // 好みで
    private static final float HEAL_ON_APPLY = 2.0f;  // 付与時少し回復

    private static final int BUFF_TTL_TICKS = 60;     // 3秒（毎秒延長）

    private int nextCheck = 0;

    // “今バフしている相手”を StudentId で保持（ワールドに1人前提）
    private StudentId currentTargetId = null;

    public KisakiBuffGoal(AbstractStudentEntity kisaki, IStudentEntity student) {
        this.kisaki = kisaki;
        this.student = student;
        this.setControls(EnumSet.noneOf(Control.class)); // 競合ゼロ
    }

    @Override
    public boolean canStart() {
        return !kisaki.getWorld().isClient && !kisaki.isLifeLockedForGoal();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        if (kisaki.getWorld().isClient) return;
        if (--nextCheck > 0) return;
        nextCheck = CHECK_INTERVAL;

        if (!(kisaki.getWorld() instanceof ServerWorld sw)) return;

        // 1) 既存ターゲットが有効なら維持（切替頻度を下げる）
        AbstractStudentEntity cur = resolveStudentById(sw, currentTargetId);
        if (isValidTarget(cur, null)) {
            UUID owner = student.getOwnerUuid();

            // (A) 既存がowner一致ならそのまま維持
            if (owner != null && owner.equals(cur.getOwnerUuid())) {
                applyAndRefresh(cur);
                return;
            }

            // (B) 既存が他人だった場合：owner一致候補が居れば乗り換え
            if (owner != null) {
                AbstractStudentEntity ownerBest = findBestInRange(sw, owner);
                if (ownerBest != null) {
                    StudentId prev = currentTargetId;

                    // 乗り換え：旧ターゲット解除
                    cur.applyKisakiSupportBuff(false, 0, 0, 0);

                    // 新ターゲット確定 & 付与
                    currentTargetId = ownerBest.getStudentId();
                    applyAndRefresh(ownerBest);

                    // 視認用：切替時だけ派手演出（お好み）
                    if (prev == null || prev != currentTargetId) {
                        spawnBuffBurst(sw, ownerBest);

                        if (kisaki instanceof com.licht_meilleur.blue_student.entity.KisakiEntity ke) {
                            ke.requestBuff(); // キサキ本体のBUFFアニメトリガー
                        }

                        // ★竜を飛ばす（切替時だけ）
                        var dragon = new com.licht_meilleur.blue_student.entity.KisakiDragonEntity(
                                BlueStudentMod.KISAKI_DRAGON, sw
                        ).setOwnerAndTarget(kisaki.getUuid(), ownerBest.getUuid());

                        Vec3d spawn = kisaki.getPos().add(0, kisaki.getHeight() * 0.6, 0);
                        dragon.setPosition(spawn.x, spawn.y, spawn.z);
                        sw.spawnEntity(dragon);
                    }

                    return;
                }
            }


            // (C) owner一致がいないなら既存のまま維持
            applyAndRefresh(cur);
            return;
        }

        // 2) 無効なら旧ターゲット解除
        if (cur != null) cur.applyKisakiSupportBuff(false, 0, 0, 0);
        currentTargetId = null;

        // 3) 新規選定：まず owner一致から1人
        UUID owner = student.getOwnerUuid();
        AbstractStudentEntity best = null;

        if (owner != null) {
            best = findBestInRange(sw, owner);
        }

        // 4) owner一致がいなければ owner不問で1人
        if (best == null) {
            best = findBestInRange(sw, null);
        }

        // 5) 見つかったら適用＋演出（切替時のみ）
        if (best != null) {
            StudentId prev = currentTargetId;

            currentTargetId = best.getStudentId();
            applyAndRefresh(best);

            // ★ターゲットが新規 or 切替のときだけ演出まとめて実行
            if (prev == null || prev != currentTargetId) {

                // ① パーティクル（任意）
                spawnBuffBurst(sw, best);

                // ② キサキ本体のBUFFアニメ
                if (kisaki instanceof com.licht_meilleur.blue_student.entity.KisakiEntity ke) {
                    ke.requestBuff();
                }

                // ③ 竜エフェクトspawn（←今回の本命）
                var dragon = new com.licht_meilleur.blue_student.entity.KisakiDragonEntity(
                        BlueStudentMod.KISAKI_DRAGON, sw
                ).setOwnerAndTarget(kisaki.getUuid(), best.getUuid());

                Vec3d spawn = kisaki.getPos().add(0, kisaki.getHeight() * 0.6, 0);
                dragon.setPosition(spawn.x, spawn.y, spawn.z);

                sw.spawnEntity(dragon);
            }
        }

    }


    /**
     * owner指定がある場合：owner一致のみから最寄りを返す
     * owner=null の場合：owner不問で最寄りを返す
     */
    private AbstractStudentEntity findBestInRange(ServerWorld sw, UUID ownerOrNull) {
        AbstractStudentEntity best = null;
        double bestD2 = 1e18;

        Box box = kisaki.getBoundingBox().expand(RANGE);
        for (AbstractStudentEntity e : sw.getEntitiesByClass(AbstractStudentEntity.class, box, x -> true)) {
            if (!isValidTarget(e, ownerOrNull)) continue;
            double d2 = kisaki.squaredDistanceTo(e);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }

    private boolean isValidTarget(AbstractStudentEntity e, UUID ownerOrNull) {
        if (e == null) return false;
        if (!e.isAlive()) return false;
        if (e == kisaki) return false;
        if (e.isLifeLockedForGoal()) return false;

        // 範囲
        double r2 = RANGE * RANGE;
        if (kisaki.squaredDistanceTo(e) > r2) return false;

        // owner指定がある時だけ縛る
        if (ownerOrNull != null) {
            if (e.getOwnerUuid() == null) return false;
            if (!ownerOrNull.equals(e.getOwnerUuid())) return false;
        }

        return true;
    }

    private void applyAndRefresh(AbstractStudentEntity target) {
        target.applyKisakiSupportBuff(true, ADD_ARMOR, ADD_MAX_HP, HEAL_ON_APPLY);
        target.setKisakiSupportTicks(BUFF_TTL_TICKS);

        // ★視認用：対象の頭上にパーティクル（毎秒1回）
        spawnBuffParticles((ServerWorld) kisaki.getWorld(), target);
    }

    private void spawnBuffParticles(ServerWorld sw, AbstractStudentEntity target) {
        Vec3d p = target.getPos().add(0, target.getHeight() + 0.25, 0);

        // 近くのプレイヤーにだけ送る（負荷対策）
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(p) > 48 * 48) continue;

            sw.spawnParticles(
                    sp,
                    ParticleTypes.HAPPY_VILLAGER, // 好みで
                    true,
                    p.x, p.y, p.z,
                    6,          // count
                    0.25, 0.15, 0.25, // spread
                    0.01        // speed
            );
        }
    }
    private void spawnBuffBurst(ServerWorld sw, AbstractStudentEntity target) {
        Vec3d p = target.getPos().add(0, target.getHeight() + 0.35, 0);
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(p) > 64 * 64) continue;

            sw.spawnParticles(sp, ParticleTypes.TOTEM_OF_UNDYING, true,
                    p.x, p.y, p.z,
                    18,
                    0.35, 0.25, 0.35,
                    0.02
            );
        }
    }




    // StudentId → 実体（ワールドに1人前提）
    private AbstractStudentEntity resolveStudentById(ServerWorld sw, StudentId id) {
        if (id == null) return null;
        var st = StudentWorldState.get(sw);
        UUID uuid = st.getStudentUuid(id);
        if (uuid == null) return null;

        var e = sw.getEntity(uuid);
        return (e instanceof AbstractStudentEntity ase) ? ase : null;
    }
}
