package com.licht_meilleur.blue_student.student;

import net.minecraft.util.StringIdentifiable;

public enum StudentId implements StringIdentifiable {
    SHIROKO("shiroko"),
    HOSHINO("hoshino"),
    HINA("hina"),
    ALICE("alice"),
    KISAKI("kisaki");

    private final String id;

    StudentId(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    public String asKey() {
        return id;
    }
}