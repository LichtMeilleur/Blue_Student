package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.entity.ShirokoDroneEntity;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.EnumSet;

public class ShirokoDroneGoal extends Goal {

    private final ShirokoEntity shiroko;


    private static final int CHECK_INTERVAL = 20;
    private static final double RANGE = 18.0;

    private int next = 0;
    private ShirokoDroneEntity drone = null;

    public ShirokoDroneGoal(ShirokoEntity shiroko) {
        this.shiroko = shiroko;
    }


    @Override
    public boolean canStart() {
        return !shiroko.getWorld().isClient && !shiroko.isLifeLockedForGoal();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void tick() {
        if (!(shiroko.getWorld() instanceof ServerWorld sw)) return;
        if (--next > 0) return;
        next = CHECK_INTERVAL;

        // 近くに敵がいる時だけ召喚維持
        boolean hasEnemy = !sw.getEntitiesByClass(
                HostileEntity.class,
                shiroko.getBoundingBox().expand(RANGE),
                e -> e.isAlive()
        ).isEmpty();

        if (!hasEnemy) {
            if (drone != null) {
                drone.discard();
                drone = null;
            }
            return;
        }

        // drone が死んだ/未生成なら生成
        if (drone == null || !drone.isAlive()) {
            drone = new ShirokoDroneEntity(sw)
                    .setOwnerUuid(shiroko.getUuid());

            drone.setPosition(shiroko.getX(), shiroko.getEyeY(), shiroko.getZ());
            sw.spawnEntity(drone);

            // ★設置アニメ
            shiroko.requestDroneStart(); // ★シロコ本体に drone_start を再生させる

        }
    }
}
