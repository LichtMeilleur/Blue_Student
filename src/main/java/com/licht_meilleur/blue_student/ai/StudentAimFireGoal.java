package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.weapon.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;

import java.util.EnumSet;

public class StudentAimFireGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    private final WeaponAction projectileAction = new ProjectileWeaponAction();
    private final WeaponAction hitscanAction = new HitscanWeaponAction();

    private LivingEntity target;
    private int aimTicks;

    // 何tick“狙う”か（1〜3で見た目調整）
    private static final int AIM_TICKS = 1;

    public StudentAimFireGoal(PathAwareEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // FOLLOW/SECURITY中のみなど条件を追加してもOK
        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        if (!student.hasQueuedFire()) return false;
        target = student.consumeQueuedFireTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        aimTicks = AIM_TICKS;
        mob.getNavigation().stop(); // ★撃つ瞬間だけ足を止める（ムーンウォーク回避にも効く）
    }

    @Override
    public boolean shouldContinue() {
        return target != null && target.isAlive() && aimTicks > 0;
    }

    @Override
    public void tick() {
        if (target == null) return;

        // ★敵の目線へ素早く向く
        mob.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ(), 90.0f, 90.0f);

        // ★本体Yawも敵へ寄せる（ムーンウォーク抑制 & 射撃の向き安定）
        float targetYaw = (float)(Math.toDegrees(Math.atan2(
                target.getZ() - mob.getZ(),
                target.getX() - mob.getX()
        )) - 90.0);

        mob.setYaw(approachAngle(mob.getYaw(), targetYaw, 35.0f)); // 35度/tickくらい
        mob.bodyYaw = mob.getYaw();
        mob.headYaw = mob.getYaw();


        aimTicks--;
        if (aimTicks > 0) return;

        // ===== 実射撃（ここで1回だけ撃つ）=====
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        boolean fired = switch (spec.type) {
            case PROJECTILE -> projectileAction.shoot(student, target, spec);
            case HITSCAN -> hitscanAction.shoot(student, target, spec);
        };

        if (fired) {
            student.requestShot(); // 演出
            if (!spec.infiniteAmmo) student.consumeAmmo(1);
        }

        // 終了
        target = null;
    }
    private float approachAngle(float cur, float target, float maxStep) {
        float delta = net.minecraft.util.math.MathHelper.wrapDegrees(target - cur);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return cur + delta;
    }


    @Override
    public void stop() {
        target = null;
    }
}
