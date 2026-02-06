package com.licht_meilleur.blue_student.student;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Vec3d;

public enum StudentId implements StringIdentifiable {
    SHIROKO("shiroko", 35, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/shiroko_face.png"),
                    new Vec3d(0.60, -0.50, 0.18)),
    HOSHINO("hoshino", 40, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/hoshino_face.png"),
            new Vec3d(0.60, -0.80, 1.00)),
    HINA("hina", 40, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/hina_face.png"),
            new Vec3d(1.60, -0.8, 0.18)),
    ALICE("alice", 30, 20,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/alice_face.png"),
            new Vec3d(0.60, -0.50, 0.18)),
    KISAKI("kisaki", 28, 18,
            new StudentAiMode[]{StudentAiMode.FOLLOW, StudentAiMode.SECURITY},
            BlueStudentMod.id("textures/gui/kisaki_face.png"),
            new Vec3d(0.60, -0.50, 0.18));

    private final String key;
    private final int baseMaxHp;
    private final int baseDefense;
    private final StudentAiMode[] allowedAis;
    private final Identifier faceTexture;
    private final Vec3d muzzleOffset;

    StudentId(String key, int baseMaxHp, int baseDefense, StudentAiMode[] allowedAis, Identifier faceTexture, Vec3d muzzleOffset) {
        this.key = key;
        this.baseMaxHp = baseMaxHp;
        this.baseDefense = baseDefense;
        this.allowedAis = allowedAis;
        this.faceTexture = faceTexture;
        this.muzzleOffset = muzzleOffset;
    }

    @Override public String asString() { return key; }

    public int getBaseMaxHp() { return baseMaxHp; }
    public int getBaseDefense() { return baseDefense; }
    public StudentAiMode[] getAllowedAis() { return allowedAis; }
    public Identifier getFaceTexture() { return faceTexture; }

    public Text getNameText() { return Text.translatable("student.blue_student." + key); }
    public Text getOnlySkillText() { return Text.translatable("skill.blue_student." + key + ".only"); }
    public Text getWeaponText() { return Text.translatable("weapon.blue_student." + key); }
    public Vec3d getMuzzleOffset() { return muzzleOffset; }
    public static StudentId fromKey(String key) {
        for (StudentId id : values()) {
            if (id.key.equals(key)) return id;
        }
        return SHIROKO;
    }
}
