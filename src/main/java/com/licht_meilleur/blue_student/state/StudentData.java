package com.licht_meilleur.blue_student.state;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class StudentData {
    public UUID uuid;
    public String dimension;
    public BlockPos pos;
    public BlockPos bed;

    public StudentData(UUID uuid, String dim, BlockPos pos, BlockPos bed) {
        this.uuid = uuid;
        this.dimension = dim;
        this.pos = pos;
        this.bed = bed;
    }
}

