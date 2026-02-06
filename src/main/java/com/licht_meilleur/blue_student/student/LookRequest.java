package com.licht_meilleur.blue_student.student;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class LookRequest {
    public LookIntentType type = LookIntentType.NONE;
    public LivingEntity target = null;  // TARGET / AWAY_FROM
    public Vec3d dir = null;            // WORLD_DIR
    public int priority = 0;
    public int holdTicks = 0;
    public Vec3d pos = null; // ★追加（POS用）


    public void clear() {
        type = LookIntentType.NONE;
        target = null;
        dir = null;
        priority = 0;
        holdTicks = 0;
        pos = null; // ★追加（POS用）

    }
}
