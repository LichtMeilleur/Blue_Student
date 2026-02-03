package com.licht_meilleur.blue_student.student;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

public enum StudentId implements StringIdentifiable {
    SHIROKO("shiroko", 35, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/shiroko_face.png")),
    HOSHINO("hoshino", 40, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/hoshino_face.png")),
    HINA("hina", 40, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/hina_face.png")),
    ALICE("alice", 30, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/alice_face.png")),
    KISAKI("kisaki", 28, 18,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/kisaki_face.png"));

    private final String key;
    private final int baseMaxHp;
    private final int baseDefense;
    private final StudentAiMode[] allowedAis;
    private final Identifier faceTexture;

    StudentId(String key, int baseMaxHp, int baseDefense, StudentAiMode[] allowedAis, Identifier faceTexture) {
        this.key = key;
        this.baseMaxHp = baseMaxHp;
        this.baseDefense = baseDefense;
        this.allowedAis = allowedAis;
        this.faceTexture = faceTexture;
    }

    @Override public String asString() { return key; }

    public int getBaseMaxHp() { return baseMaxHp; }
    public int getBaseDefense() { return baseDefense; }
    public StudentAiMode[] getAllowedAis() { return allowedAis; }
    public Identifier getFaceTexture() { return faceTexture; }

    public Text getNameText() { return Text.translatable("student.blue_student." + key); }
    public Text getOnlySkillText() { return Text.translatable("skill.blue_student." + key + ".only"); }
    public Text getWeaponText() { return Text.translatable("weapon.blue_student." + key); }

    public static StudentId fromKey(String key) {
        for (StudentId id : values()) {
            if (id.key.equals(key)) return id;
        }
        return SHIROKO;
    }
}
