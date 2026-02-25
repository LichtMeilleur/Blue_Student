package com.licht_meilleur.blue_student.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class SonicBeamEntity extends Entity {

    private UUID ownerUuid;
    private UUID targetUuid;

    private Vec3d start = Vec3d.ZERO;
    private Vec3d end = Vec3d.ZERO;

    private int life = 20;

    public SonicBeamEntity(EntityType<? extends SonicBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void init(Vec3d start, Vec3d end) {
        this.start = start;
        this.end = end;
    }

    public Vec3d getStart() { return start; }
    public Vec3d getEnd() { return end; }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        LivingEntity owner = (LivingEntity) sw.getEntity(ownerUuid);
        LivingEntity target = (LivingEntity) sw.getEntity(targetUuid);

        if (owner == null || target == null) {
            discard();
            return;
        }

        start = owner.getEyePos();
        end = target.getEyePos();

        life--;
        if (life <= 0) discard();
    }



    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
