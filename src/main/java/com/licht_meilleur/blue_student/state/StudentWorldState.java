package com.licht_meilleur.blue_student.state;

import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StudentWorldState extends PersistentState {

    private static final String NAME = "blue_student_state";

    // StudentId(asString) -> UUID
    private final Map<String, UUID> studentById = new HashMap<>();

    public static StudentWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                StudentWorldState::createFromNbt,
                StudentWorldState::new,
                NAME
        );
    }

    // ===== API（sid対応）=====

    public boolean hasStudent(StudentId sid) {
        return sid != null && studentById.containsKey(sid.asString());
    }

    public UUID getStudentUuid(StudentId sid) {
        return (sid == null) ? null : studentById.get(sid.asString());
    }

    public void setStudent(StudentId sid, UUID uuid) {
        if (sid == null || uuid == null) return;
        studentById.put(sid.asString(), uuid);
        markDirty();
    }

    public void clearStudent(StudentId sid) {
        if (sid == null) return;
        studentById.remove(sid.asString());
        markDirty();
    }

    // （任意）デバッグや移行用：全部消す
    public void clearAll() {
        studentById.clear();
        markDirty();
    }

    // ===== NBT =====

    public static StudentWorldState createFromNbt(NbtCompound nbt) {
        StudentWorldState s = new StudentWorldState();

        // 新形式：Students(list)
        if (nbt.contains("Students", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Students", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);
                String sid = tag.getString("Sid");
                if (!sid.isEmpty() && tag.containsUuid("Uuid")) {
                    s.studentById.put(sid, tag.getUuid("Uuid"));
                }
            }
            return s;
        }

        // 旧形式（あなたの今の保存）：Uuid + Alive
        // 旧データはどの生徒か判別できないので「全消しで移行」してOK
        // ※残したいなら、どれか1つに割り当てる必要が出る
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : studentById.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putString("Sid", e.getKey());
            tag.putUuid("Uuid", e.getValue());
            list.add(tag);
        }
        nbt.put("Students", list);
        return nbt;
    }
}
