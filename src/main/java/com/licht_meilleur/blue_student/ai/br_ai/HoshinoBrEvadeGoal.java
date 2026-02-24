package com.licht_meilleur.blue_student.ai.br_ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

public class HoshinoBrEvadeGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private LivingEntity threat;
    private int repathCooldown = 0;

    // BRホシノは「撃ちながら下がる/横にズレる」前提の回避
    private static final int REPATH_INTERVAL = 8;
    private static final double EVADE_SPEED = 1.35;

    // 近すぎ判定に少し余裕
    private static final double TOO_CLOSE_PAD = 0.75;

    // “回避しながら撃つ” を定期的にキュー
    private int firePulse = 0;
    private static final int FIRE_PULSE_INTERVAL = 6; // 4〜8で好み

    public HoshinoBrEvadeGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;
        if (mob instanceof AbstractStudentEntity ase && ase.isBrActionActiveServer()) return false;

        if (student instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity ase) {
            if (ase.getForm() != com.licht_meilleur.blue_student.student.StudentForm.BR) return false;
        }

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        // “今のターゲット” がいるならそれを優先、無ければ付近の敵を拾う
        threat = mob.getTarget();
        if (threat == null || !threat.isAlive()) {
            threat = findNearestHostile();
        }
        if (threat == null) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        double dist = mob.distanceTo(threat);

        // 近すぎ or パニック距離なら回避開始
        return dist < (spec.preferredMinRange + TOO_CLOSE_PAD) || dist < spec.panicRange;
    }

    @Override
    public boolean shouldContinue() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        if (threat == null || !threat.isAlive()) return false;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(threat);

        // 十分離れたら終了（“常時回避”にならないように）
        return dist < (spec.preferredMinRange + 2.0);
    }

    @Override
    public void start() {
        student.setEvading(true);
        repathCooldown = 0;
        firePulse = 0;

        // 回避開始時は「敵から目を逸らす（逃げる方向を見る）」
        student.requestLookAwayFrom(threat, 80, 6);
    }

    @Override
    public void stop() {
        student.setEvading(false);
        threat = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.getWorld() instanceof ServerWorld)) return;
        if (threat == null || !threat.isAlive()) return;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(threat);

        if (repathCooldown > 0) repathCooldown--;

        // 近い間は「逃げる方向を向く」
        student.requestLookAwayFrom(threat, 80, 2);

        // 逃げ先を更新
        if (repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL;

            Vec3d away = mob.getPos().subtract(threat.getPos());
            away = new Vec3d(away.x, 0, away.z);
            if (away.lengthSquared() > 1.0e-6) {
                Vec3d desired = mob.getPos().add(away.normalize().multiply(6.0));

                // 横ズレも少し混ぜる（単純後退で詰まるのを減らす）
                Vec3d right = new Vec3d(-away.z, 0, away.x).normalize();
                double side = (mob.getRandom().nextBoolean() ? 1.0 : -1.0) * 2.0;
                desired = desired.add(right.multiply(side));

                Vec3d pos = FuzzyTargeting.findFrom(mob, 12, 7, desired);
                if (pos == null) pos = desired;

                mob.getNavigation().startMovingTo(pos.x, pos.y, pos.z, EVADE_SPEED);
            }
        }

    }

    private LivingEntity findNearestHostile() {
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        Box box = mob.getBoundingBox().expand(spec.range + 6.0);

        return mob.getWorld()
                .getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof HostileEntity)
                .stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}