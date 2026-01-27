package com.licht_meilleur.blue_student.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.UUID;

public class StudentWorldState extends PersistentState {

    private static final String NAME = "blue_student_state";

    private UUID studentUuid;
    private boolean alive;

    public static StudentWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                StudentWorldState::createFromNbt,
                StudentWorldState::new,
                NAME
        );
    }

    public boolean hasStudent() {
        return alive && studentUuid != null;
    }

    public void setStudent(UUID uuid) {
        this.studentUuid = uuid;
        this.alive = true;
        markDirty();
    }

    public void clearStudent() {
        this.studentUuid = null;
        this.alive = false;
        markDirty();
    }

    public UUID getStudentUuid() {
        return studentUuid;
    }

    // ===== NBT =====

    public static StudentWorldState createFromNbt(NbtCompound nbt) {
        StudentWorldState s = new StudentWorldState();

        if (nbt.containsUuid("Uuid"))
            s.studentUuid = nbt.getUuid("Uuid");

        s.alive = nbt.getBoolean("Alive");

        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (studentUuid != null)
            nbt.putUuid("Uuid", studentUuid);

        nbt.putBoolean("Alive", alive);
        return nbt;
    }
}