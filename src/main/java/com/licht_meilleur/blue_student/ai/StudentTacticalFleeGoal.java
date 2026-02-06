package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * 戦闘を潰さない「条件付き退避」。
 * - 危険距離(panicRange)に入った
 * - リロード中で危険
 * - 角詰まりっぽい（horizontalCollision + 低速）
 */
public class StudentTacticalFleeGoal extends Goal {
    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private LivingEntity threat;
    private int repathCooldown = 0;

    // 調整
    private static final int REPATH_INTERVAL = 10;
    private static final double BASE_SPEED = 1.25;
    private static final double SEARCH_RADIUS_EXTRA = 6.0;

    public StudentTacticalFleeGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE)); // LOOKは握らない（向きはAimFireに寄せる）
    }

    @Override
    public boolean canStart() {
        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        threat = findNearestHostile(Math.max(8.0, spec.range) + SEARCH_RADIUS_EXTRA);
        if (threat == null) return false;

        double dist = mob.distanceTo(threat);

        // ★戦闘を潰さない条件：
        // AimFire が撃つ直前（queuedFireがある）なら基本退避しない
        // ただし「panicRange内」だけは退避OK（近接に殴られるのを避ける）
        boolean hasQueued = student.hasQueuedFire();
        if (hasQueued && dist > spec.panicRange) return false;

        // ① 危険距離（panic）に入ったら退避
        if (dist < spec.panicRange) return true;

        // ② リロード中で近いなら退避
        if (student.isReloading() && dist < Math.max(spec.panicRange + 1.5, spec.preferredMinRange + 2.0)) return true;

        // ③ 角詰まりっぽい（押しつけ）なら短時間退避して角を外す
        boolean cornerish = mob.horizontalCollision && mob.getVelocity().horizontalLengthSquared() < 0.0009;
        if (cornerish && dist < spec.preferredMinRange + 3.0) return true;

        return false;
    }

    @Override
    public boolean shouldContinue() {
        if (threat == null || !threat.isAlive()) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(threat);

        // panicから抜けたら終了（少し余裕を持たせる）
        return dist < (spec.panicRange + 2.0);
    }

    @Override
    public void start() {
        repathCooldown = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        threat = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (threat == null || !threat.isAlive()) {
            threat = findNearestHostile(14.0);
            if (threat == null) return;
        }

        if (repathCooldown > 0) { repathCooldown--; return; }
        repathCooldown = REPATH_INTERVAL;

        // 逃げ方向
        Vec3d away = mob.getPos().subtract(threat.getPos());
        away = new Vec3d(away.x, 0, away.z);
        if (away.lengthSquared() < 1.0e-6) return;
        away = away.normalize();

        // 逃げ先候補（FuzzyTargetingで角を避けやすい）
        Vec3d desired = mob.getPos().add(away.multiply(7.0));
        Vec3d pos = FuzzyTargeting.findFrom(mob, 12, 7, desired);
        if (pos == null) pos = desired;

        mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, BASE_SPEED);
        // ★向きはMOVE/Lookは握らない（AimFireや他のGoalに任せる）
    }

    private LivingEntity findNearestHostile(double r) {
        Box box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && e instanceof HostileEntity
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}
