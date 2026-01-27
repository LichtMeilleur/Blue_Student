package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class StudentSecurityGoal extends Goal {
    private final PathAwareEntity mob;
    private final ISecurityPosProvider security;
    private final IStudentEntity student;
    private final double speed;

    private int repathCooldown = 0;

    private static final int MODE_SECURITY = 1;
    private static final int REPATH_INTERVAL = 10;
    private static final double RETURN_DIST = 3.0;

    /**
     * securityPos を持ってるEntity用の小インターフェース（Shiroko以外も使える）
     */
    public interface ISecurityPosProvider {
        BlockPos getSecurityPos();
        void setSecurityPos(BlockPos pos);
    }

    public StudentSecurityGoal(PathAwareEntity mob, IStudentEntity student, ISecurityPosProvider security, double speed) {
        this.mob = mob;
        this.student = student;
        this.security = security;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return student.getAiMode() == StudentAiMode.SECURITY;
    }

    @Override
    public boolean shouldContinue() {
        return student.getAiMode() == StudentAiMode.SECURITY;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        BlockPos p = security.getSecurityPos();
        if (p == null) {
            security.setSecurityPos(mob.getBlockPos());
            return;
        }

        double dist2 = mob.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);

        if (dist2 > RETURN_DIST * RETURN_DIST) {
            if (repathCooldown > 0) {
                repathCooldown--;
                return;
            }
            repathCooldown = REPATH_INTERVAL;

            mob.getNavigation().startMovingTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
        } else {
            mob.getNavigation().stop();
        }
    }
}
