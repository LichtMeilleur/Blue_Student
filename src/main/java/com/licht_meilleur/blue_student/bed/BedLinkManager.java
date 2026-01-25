package com.licht_meilleur.blue_student.bed;

import com.licht_meilleur.blue_student.student.StudentId;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedLinkManager {
    private static final Map<UUID, StudentId> LINKING = new ConcurrentHashMap<>();

    public static void setLinking(UUID playerUuid, StudentId id) {
        LINKING.put(playerUuid, id);
    }

    public static StudentId getLinking(UUID playerUuid) {
        return LINKING.get(playerUuid);
    }

    public static void clearLinking(UUID playerUuid) {
        LINKING.remove(playerUuid);
    }
}