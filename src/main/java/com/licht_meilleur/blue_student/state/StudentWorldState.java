package com.licht_meilleur.blue_student.state;

import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StudentWorldState extends PersistentState {

    private static final String NAME = "blue_student_state";

    // sid -> full data
    private final Map<String, StudentData> studentById = new HashMap<>();


    // =====================================================
    // Overworld固定保存（全ディメンション共通DB）
    // =====================================================
    public static StudentWorldState get(ServerWorld anyWorld) {
        MinecraftServer server = anyWorld.getServer();
        return get(server);
    }

    public static StudentWorldState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                StudentWorldState::createFromNbt,
                StudentWorldState::new,
                NAME
        );
    }


    // =====================================================
    // API
    // =====================================================

    public boolean hasStudent(StudentId sid) {
        return studentById.containsKey(sid.asString());
    }

    public UUID getStudentUuid(StudentId sid) {
        StudentData d = studentById.get(sid.asString());
        return d == null ? null : d.uuid;
    }

    public StudentData getData(StudentId sid) {
        return studentById.get(sid.asString());
    }


    // ★生成/更新（spawn時）
    public void setStudent(StudentId sid, UUID uuid, ServerWorld world, BlockPos pos) {
        studentById.put(
                sid.asString(),
                new StudentData(
                        uuid,
                        world.getRegistryKey().getValue().toString(),
                        pos,
                        null
                )
        );
        markDirty();
    }


    // ★位置更新（tickで使うと便利）
    public void updatePos(StudentId sid, ServerWorld world, BlockPos pos) {
        StudentData d = getData(sid);
        if (d == null) return;

        d.dimension = world.getRegistryKey().getValue().toString();
        d.pos = pos;
        markDirty();
    }

    // ★ベッド登録
    public void setBed(StudentId sid, BlockPos bed) {
        StudentData d = getData(sid);
        if (d == null) return;

        d.bed = bed;
        markDirty();
    }

    public BlockPos getBed(StudentId sid) {
        StudentData d = getData(sid);
        return d == null ? null : d.bed;
    }

    public void clearStudent(StudentId sid) {
        studentById.remove(sid.asString());
        markDirty();
    }

    public void clearAll() {
        studentById.clear();
        markDirty();
    }


    // =====================================================
    // NBT load
    // =====================================================
    public static StudentWorldState createFromNbt(NbtCompound nbt) {
        StudentWorldState s = new StudentWorldState();

        if (nbt.contains("Students", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Students", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);

                String sid = tag.getString("Sid");
                if (sid.isEmpty()) continue;
                if (!tag.containsUuid("Uuid")) continue;

                UUID uuid = tag.getUuid("Uuid");

                String dim = tag.contains("Dim")
                        ? tag.getString("Dim")
                        : "minecraft:overworld";

                BlockPos pos = tag.contains("Pos")
                        ? BlockPos.fromLong(tag.getLong("Pos"))
                        : null;

                BlockPos bed = tag.contains("Bed")
                        ? BlockPos.fromLong(tag.getLong("Bed"))
                        : null;

                s.studentById.put(sid, new StudentData(uuid, dim, pos, bed));
            }
        }

        return s;
    }


    // =====================================================
    // NBT save
    // =====================================================
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {

        NbtList list = new NbtList();

        for (var e : studentById.entrySet()) {
            StudentData d = e.getValue();

            NbtCompound tag = new NbtCompound();
            tag.putString("Sid", e.getKey());
            tag.putUuid("Uuid", d.uuid);

            if (d.dimension != null) tag.putString("Dim", d.dimension);
            if (d.pos != null) tag.putLong("Pos", d.pos.asLong());
            if (d.bed != null) tag.putLong("Bed", d.bed.asLong());

            list.add(tag);
        }

        nbt.put("Students", list);
        return nbt;
    }


    // =====================================================
    // StudentData
    // =====================================================
    public static class StudentData {
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
}
