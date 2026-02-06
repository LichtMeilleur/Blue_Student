package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.HinaEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

public class HinaStrafeFlyGoal extends Goal {
    private final HinaEntity mob;
    private final IStudentEntity student;

    private LivingEntity target;

    private int tick;
    private double angle;
    private int dir = 1;

    // 調整
    private static final double RADIUS = 8.0;     // 周回半径
    private static final double SPEED  = 4;    // 飛行速度
    private static final double ALT_MIN = 3.0;    // 敵より上に居る高さ
    private static final double ALT_MAX = 6.0;
    private static final int UPDATE_INTERVAL = 5; // 何tick毎に目標点更新

    public HinaStrafeFlyGoal(HinaEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!mob.isFlying()) return false;

        // 戦闘中っぽい時だけ動かす（FOLLOW/SECURITY は好み）
        // 「近くに敵がいる」なら周回する、でOK
        target = findNearestHostile();
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        return mob.isFlying() && target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick = 0;
        angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
        dir = mob.getRandom().nextBoolean() ? 1 : -1;
    }

    @Override
    public void stop() {
        target = null;
        // ※CombatGoalとケンカしないため、ここで stop してもOK
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        tick++;

        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            if (target == null) return;
        }

        // たまに回転方向を変えて単調さを無くす
        if (tick % 60 == 0 && mob.getRandom().nextFloat() < 0.4f) dir *= -1;

        if (tick % UPDATE_INTERVAL != 0) return;

        // 高度：敵の上
        double baseY = target.getY() + ALT_MIN + mob.getRandom().nextDouble() * (ALT_MAX - ALT_MIN);

        // 周回角度
        angle += dir * 0.25;

        double dx = Math.cos(angle) * RADIUS;
        double dz = Math.sin(angle) * RADIUS;

        Vec3d desired = new Vec3d(target.getX() + dx, baseY, target.getZ() + dz);

        // 目標点が壁っぽいなら角度を追加でずらす（簡易）
        if (isBlocked(desired)) {
            angle += dir * 0.9;
            dx = Math.cos(angle) * RADIUS;
            dz = Math.sin(angle) * RADIUS;
            desired = new Vec3d(target.getX() + dx, baseY, target.getZ() + dz);
        }

        mob.getNavigation().startMovingTo(desired.x, desired.y, desired.z, SPEED);
    }

    private boolean isBlocked(Vec3d p) {
        BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
        var w = mob.getWorld();
        return !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()
                || !w.getBlockState(bp.up()).getCollisionShape(w, bp.up()).isEmpty();
    }

    private LivingEntity findNearestHostile() {
        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double r = Math.max(12.0, spec.range + 8.0);

        Box box = mob.getBoundingBox().expand(r);
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && e instanceof HostileEntity
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }
}
