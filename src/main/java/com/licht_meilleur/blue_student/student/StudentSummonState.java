package com.licht_meilleur.blue_student.student;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StudentSummonState extends PersistentState {

    private static final String KEY = "blue_student_summons";

    // owner -> (sid -> entityUuid)
    private final Map<UUID, Map<String, UUID>> map = new HashMap<>();

    public static StudentSummonState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                StudentSummonState::fromNbt,
                StudentSummonState::new,
                KEY
        );
    }

    public UUID getSummonedEntity(UUID owner, StudentId sid) {
        Map<String, UUID> inner = map.get(owner);
        if (inner == null) return null;
        return inner.get(sid.asString());
    }

    public void setSummonedEntity(UUID owner, StudentId sid, UUID entityUuid) {
        map.computeIfAbsent(owner, k -> new HashMap<>()).put(sid.asString(), entityUuid);
        markDirty();
    }

    public void clear(UUID owner, StudentId sid) {
        Map<String, UUID> inner = map.get(owner);
        if (inner == null) return;
        inner.remove(sid.asString());
        if (inner.isEmpty()) map.remove(owner);
        markDirty();
    }

    // --------------------
    // NBT save/load
    // --------------------
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound root = new NbtCompound();

        for (var e : map.entrySet()) {
            UUID owner = e.getKey();
            NbtCompound perOwner = new NbtCompound();
            for (var s : e.getValue().entrySet()) {
                perOwner.putUuid(s.getKey(), s.getValue()); // key = sid.asString()
            }
            root.put(owner.toString(), perOwner);
        }

        nbt.put("Data", root);
        return nbt;
    }

    private static StudentSummonState fromNbt(NbtCompound nbt) {
        StudentSummonState st = new StudentSummonState();
        if (!nbt.contains("Data")) return st;

        NbtCompound root = nbt.getCompound("Data");
        for (String ownerStr : root.getKeys()) {
            try {
                UUID owner = UUID.fromString(ownerStr);
                NbtCompound perOwner = root.getCompound(ownerStr);

                Map<String, UUID> inner = new HashMap<>();
                for (String sidKey : perOwner.getKeys()) {
                    if (perOwner.containsUuid(sidKey)) {
                        inner.put(sidKey, perOwner.getUuid(sidKey));
                    }
                }
                if (!inner.isEmpty()) st.map.put(owner, inner);
            } catch (Exception ignored) {}
        }

        return st;
    }
}