package com.licht_meilleur.blue_student.bed;

import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedLinkManager {
    private static final Map<UUID, StudentId> LINKING = new ConcurrentHashMap<>();

    // ★ owner -> (student -> footPos)
    private static final Map<UUID, Map<StudentId, BlockPos>> BEDS = new ConcurrentHashMap<>();

    public static void setLinking(UUID playerUuid, StudentId id) {
        LINKING.put(playerUuid, id);
    }

    public static StudentId getLinking(UUID playerUuid) {
        return LINKING.get(playerUuid);
    }

    public static void clearLinking(UUID playerUuid) {
        LINKING.remove(playerUuid);
    }

    public static BlockPos getBedPos(UUID playerUuid, StudentId id) {
        var m = BEDS.get(playerUuid);
        return (m == null) ? null : m.get(id);
    }

    public static void setBedPos(UUID playerUuid, StudentId id, BlockPos footPos) {
        BEDS.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(id, footPos);
    }

    public static void clearBedPos(UUID playerUuid, StudentId id) {
        var m = BEDS.get(playerUuid);
        if (m != null) m.remove(id);
    }
}
