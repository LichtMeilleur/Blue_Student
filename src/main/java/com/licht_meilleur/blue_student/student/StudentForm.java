package com.licht_meilleur.blue_student.student;


public enum StudentForm {
    NORMAL(0, "normal"),
    BR(1, "br");

    private final int id;
    private final String key;

    StudentForm(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }
    public String asString() { return key; }

    public static StudentForm fromId(int id) {
        for (var f : values()) if (f.id == id) return f;
        return NORMAL;
    }

    public static StudentForm fromKey(String key) {
        for (var f : values()) if (f.key.equalsIgnoreCase(key)) return f;
        return NORMAL;
    }
}