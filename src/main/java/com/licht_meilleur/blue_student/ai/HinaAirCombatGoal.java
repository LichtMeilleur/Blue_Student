package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.HinaEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;



import java.util.Comparator;
import java.util.EnumSet;

public class HinaAirCombatGoal extends Goal {
    private final HinaEntity mob;
    private final IStudentEntity student;

    private LivingEntity target;
    private int replanTicks = 0;
    private boolean cw; // clockwise

    // 調整値
    private static final double ALT = 4.0;      // 上空高さ
    private static final double R_MIN = 7.0;    // 近すぎたら離れる
    private static final double R_MAX = 12.0;   // 遠すぎたら詰める
    private static final int REPLAN = 5;        // 何tickごとに目標更新
    private static final double SPEED = 1.2;    // 体感に直結（まず 2.5〜4.0）

    private int fireCd = 0; // 空中での射撃キュー間隔（Aimが撃つまでの要求頻度）


    private static final double RAY_DOWN = 48.0; // 真下検索距離（十分長く）
    // 高度（地面から何ブロック浮くか）
    private static final double HOVER_AGL = 6.0; // ← 好きに調整（4〜8くらいが自然）


    public HinaAirCombatGoal(HinaEntity mob, IStudentEntity student) {
        this.mob = mob;
        this.student = student;
        this.setControls(EnumSet.of(Control.MOVE)); // LOOKはAim側に任せるならMOVEだけ
    }

    @Override
    public boolean canStart() {
        if (!mob.isFlying()) return false;

        target = findNearestHostile(); // ★自前で探す
        return target != null && target.isAlive();
    }


    @Override
    public boolean shouldContinue() {
        return mob.isFlying() && target != null && target.isAlive();
    }

    @Override
    public void start() {
        cw = mob.getRandom().nextBoolean();
        replanTicks = 0;
        fireCd = 0;                 // ★忘れず初期化
        mob.setFlyShooting(true);
    }

    @Override
    public void stop() {
        mob.setFlyShooting(false);
        target = null;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            return;
        }

        // ===== 1) 目線＆射撃キュー：毎tick =====
        student.requestLookTarget(target, 80, 2);

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());
        double dist = mob.distanceTo(target);

        if (fireCd > 0) fireCd--;

        if (dist <= spec.range) {
            if (fireCd == 0) {
                student.queueFire(target);
                fireCd = Math.max(1, spec.cooldownTicks); // HINAなら2のはず
            }
        }

        // ===== 2) 移動目標の再計算：5tickに1回 =====
        if (replanTicks-- > 0) return;
        replanTicks = REPLAN;

        Vec3d tp = target.getPos();
        Vec3d my = mob.getPos();

        Vec3d toMe = my.subtract(tp);
        Vec3d flat = new Vec3d(toMe.x, 0, toMe.z);
        double d = Math.sqrt(flat.lengthSquared());

        if (d < 1e-3) {
            flat = new Vec3d(1, 0, 0);
            d = 1;
        } else {
            flat = flat.normalize();
        }

        Vec3d tangent = cw
                ? new Vec3d(-flat.z, 0, flat.x)
                : new Vec3d(flat.z, 0, -flat.x);

        double desiredR = (d < R_MIN) ? R_MAX : (d > R_MAX ? R_MIN : d);
        Vec3d radialPos = tp.add(flat.multiply(desiredR));
        Vec3d orbitPos  = radialPos.add(tangent.multiply(3.0));


        double t = mob.age * 0.15;
        orbitPos = orbitPos.add(Math.cos(t) * 1.2, 0, Math.sin(t) * 1.2); // 1.2は好み


// ===== 高度制御（地面基準）=====
        double groundY = findGroundY();          // 真下の地面Y
        double desiredY = groundY + HOVER_AGL;   // 地面から一定高度
        double myY = mob.getY();

// 「いきなり desiredY に吸い寄せられない」ためのP制御（少しずつ寄せる）
        double err = desiredY - myY;
        double step = clamp(err * 0.25, -0.35, 0.35);   // 0.25と0.35は体感で調整
        double goalY = myY + step;

// ついでに “ターゲットより高くなりすぎない” 安全柵（好み）
        double maxAboveTarget = 6.0;
        goalY = Math.min(goalY, target.getEyeY() + maxAboveTarget);

// goal
        Vec3d goal = new Vec3d(orbitPos.x, goalY, orbitPos.z);
        mob.getMoveControl().moveTo(goal.x, goal.y, goal.z, SPEED);


    }

    private LivingEntity findNearestHostile() {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return null;

        WeaponSpec spec = WeaponSpecs.forStudent(student.getStudentId());

        // 探索半径：射程ベース（最低でもそれなり）
        double r = Math.max(12.0, spec.range);

        Box box = mob.getBoundingBox().expand(r);

        return sw.getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive()
                                && e instanceof HostileEntity
                                && e != mob
                ).stream()
                .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                .orElse(null);
    }

    private double findGroundY() {
        var w = mob.getWorld();

        Vec3d from = mob.getPos().add(0, 0.2, 0);
        Vec3d to   = from.add(0, -48.0, 0);

        var hit = w.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mob
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos().y;
        }

        return mob.getY() - 2.0;
    }


    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
