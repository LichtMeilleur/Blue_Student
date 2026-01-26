package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

public class StudentFollowGoal extends Goal {
    private final ShirokoEntity student;
    private final double speed;
    private PlayerEntity owner;

    // ★パス再計算の間隔（tick）
    private int repathCooldown = 0;

    public StudentFollowGoal(ShirokoEntity student, double speed) {
        this.student = student;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (student.getAiMode() != 0) return false;
        owner = student.getOwnerPlayer();
        return owner != null && owner.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        if (student.getAiMode() != 0) return false;
        if (owner == null || !owner.isAlive()) return false;

        // 近いなら継続しない（無駄に動かさない）
        return student.squaredDistanceTo(owner) > (2.0 * 2.0);
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        student.getNavigation().stop();
        owner = null;
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // 視線だけは毎tickでも軽い
        student.getLookControl().lookAt(owner, 30.0f, 30.0f);

        double dist2 = student.squaredDistanceTo(owner);
        if (dist2 < (2.0 * 2.0)) {
            student.getNavigation().stop();
            return;
        }

        // ★パス再計算を間引く（例：10tickに1回）
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 10;

        // ★ベタに owner へ（毎tick “背後座標” を投げない）
        student.getNavigation().startMovingTo(owner, speed);
    }
}