package com.licht_meilleur.blue_student.student;

import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public enum StudentAiMode {
    FOLLOW(0, "aimode.blue_student.follow"),
    SECURITY(1, "aimode.blue_student.security");
    // COOKING(2, "aimode.blue_student.cooking") ←将来こう増やせる

    public final int id;
    private final String langKey;

    StudentAiMode(int id, String langKey) {
        this.id = id;
        this.langKey = langKey;
    }

    public Text getText() {
        return Text.translatable(langKey);
    }

    public static StudentAiMode fromId(int id) {
        for (var m : values()) if (m.id == id) return m;
        return FOLLOW;
    }


    // ★ DataTracker用ハンドラ（enumをintで同期）
    public static final TrackedDataHandler<StudentAiMode> TRACKED = new TrackedDataHandler<>() {
        @Override public void write(PacketByteBuf buf, StudentAiMode value) { buf.writeVarInt(value.id); }
        @Override public StudentAiMode read(PacketByteBuf buf) { return fromId(buf.readVarInt()); }
        @Override public StudentAiMode copy(StudentAiMode value) { return value; }
    };

    static {
        TrackedDataHandlerRegistry.register(TRACKED);
    }
}