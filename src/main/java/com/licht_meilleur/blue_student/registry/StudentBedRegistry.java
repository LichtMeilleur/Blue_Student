package com.licht_meilleur.blue_student.registry;

import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class StudentBedRegistry {

    private static final Map<StudentId, BlockPos> BEDS = new HashMap<>();

    public static BlockPos get(StudentId id) {
        return BEDS.get(id);
    }

    public static void set(StudentId id, BlockPos pos) {
        BEDS.put(id, pos);
    }

    public static void clear(StudentId id) {
        BEDS.remove(id);
    }
}
