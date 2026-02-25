package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.AliceEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentForm;
import com.licht_meilleur.blue_student.weapon.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;

import java.util.EnumSet;

public class AliceHyperShotGoal extends Goal {

    private final AbstractStudentEntity mob;
    private final IStudentEntity student;

    private final WeaponAction hitscan = new HitscanWeaponAction();

    private static final int COOLDOWN = 160;   // 8秒
    private static final int CHARGE_TICKS = 20;

    private int cooldown = 0;
    private int charging = 0;

    private LivingEntity target;

    public AliceHyperShotGoal(AbstractStudentEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;

        // 移動停止だけ使う
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {

        if (mob.getForm() == StudentForm.BR) return false;

        if (mob.getWorld().isClient) return false;

        if (mob.isLifeLockedForGoal()) return false;


        // ★ここが重要：Goalが走ってなくても毎tick減算される
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        // ★ mob.getTarget() 使わない！！
        target = findNearestEnemy();

        return target != null;
    }


    @Override
    public boolean shouldContinue() {
        // ★ cooldownも含めて回す
        return charging > 0 && target != null && target.isAlive();
    }


    @Override
    public void start() {
        charging = CHARGE_TICKS;
    }

    @Override
    public void stop() {
        charging = 0;
        target = null;
    }

    @Override
    public void tick() {
        if (charging <= 0) return;

        charging--;

        // 停止（チャージ演出）
        mob.getNavigation().stop();
        mob.setVelocity(0, 0, 0);

        // 照準固定
        if (target != null) student.requestLookTarget(target, 5, 5);

        // チャージ完了 → 発射
        if (charging == 0 && target != null && target.isAlive()) {
            if (mob instanceof AliceEntity ae) {
                ae.requestHyperShot(); // アニメトリガー
            }
            hitscan.shoot(student, target, WeaponSpecs.ALICE_HYPER);

            cooldown = COOLDOWN; // ここで再設定
        }
    }

    private LivingEntity findNearestEnemy() {
        var world = mob.getWorld();

        double range = 40.0;

        LivingEntity best = null;
        double bestD2 = 999999;

        for (HostileEntity e : world.getEntitiesByClass(
                HostileEntity.class,
                mob.getBoundingBox().expand(range),
                x -> x.isAlive()


        )) {
            double d2 = mob.squaredDistanceTo(e);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }

        return best;
    }

}
