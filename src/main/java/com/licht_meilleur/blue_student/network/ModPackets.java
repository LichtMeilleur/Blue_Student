package com.licht_meilleur.blue_student.network;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import net.minecraft.entity.Entity;

import java.util.UUID;

public class ModPackets {
    static {
        System.out.println("[BlueStudent] ModPackets class loaded");
        BlueStudentMod.LOGGER.info("[BlueStudent] ModPackets class loaded");
    }
    public static final Identifier SET_AI_MODE  = BlueStudentMod.id("set_ai_mode");
    public static final Identifier CALL_STUDENT = BlueStudentMod.id("call_student");
    public static final Identifier CALL_BACK_STUDENT = BlueStudentMod.id("call_back_student");

    public static final Identifier S2C_SHOT_FX = BlueStudentMod.id("s2c_shot_fx");
    // ★ 64にしたければ 64 にする
    private static final int COST_DIAMOND = 64;

    public static void registerC2S() {

        // =========================================
        // AI MODE
        // =========================================
        ServerPlayNetworking.registerGlobalReceiver(SET_AI_MODE, (server, player, handler, buf, responseSender) -> {

            final int entityId = buf.readInt();
            final int modeId = buf.readInt();

            server.execute(() -> {
                var raw = player.getWorld().getEntityById(entityId);
                if (!(raw instanceof IStudentEntity se)) return;

                se.setAiMode(StudentAiMode.fromId(modeId));
            });
        });



        // =========================================
        // CALL (召喚)
        // =========================================
        ServerPlayNetworking.registerGlobalReceiver(CALL_STUDENT, (server, player, handler, buf, responseSender) -> {

            final String sidStr = buf.readString(64);
            final BlockPos tabletPos = buf.readBlockPos();

            server.execute(() -> {

                ServerWorld sw = player.getServerWorld();
                StudentWorldState state = StudentWorldState.get(sw);

                StudentId sid = parseStudentId(sidStr);

                if (state.hasStudent(sid)) {
                    player.sendMessage(Text.literal("Already summoned"), false);
                    return;
                }

                Entity raw = switch (sid) {
                    case SHIROKO -> BlueStudentMod.SHIROKO.create(sw);
                    case HOSHINO -> BlueStudentMod.HOSHINO.create(sw);
                    case HINA    -> BlueStudentMod.HINA.create(sw);
                    case ALICE   -> BlueStudentMod.ALICE.create(sw);
                    case KISAKI  -> BlueStudentMod.KISAKI.create(sw);
                };

                if (!(raw instanceof IStudentEntity se)) return;

                BlockPos spawn = tabletPos.up();

                raw.refreshPositionAndAngles(
                        spawn.getX() + 0.5,
                        spawn.getY(),
                        spawn.getZ() + 0.5,
                        player.getYaw(),
                        0
                );

                se.setOwnerUuid(player.getUuid());

                sw.spawnEntity(raw);

                // ★位置＋DIM保存（重要）
                state.setStudent(sid, raw.getUuid(), sw, spawn);

                player.sendMessage(Text.literal("Summoned"), false);
            });
        });



        // =========================================
        // CALL BACK（完全版）
        // =========================================
        ServerPlayNetworking.registerGlobalReceiver(CALL_BACK_STUDENT, (server, player, handler, buf, responseSender) -> {

            final String sidStr = buf.readString(64);
            final BlockPos tabletPos = buf.readBlockPos();

            server.execute(() -> {

                ServerWorld sw = player.getServerWorld();
                StudentWorldState state = StudentWorldState.get(sw);

                StudentId sid = parseStudentId(sidStr);

                UUID uuid = state.getStudentUuid(sid);
                if (uuid == null) return;

                BlockPos spawn = tabletPos.up();

                // ======================
                // 同ディメンション
                // ======================
                Entity found = sw.getEntity(uuid);

                if (found != null && found.isAlive()) {
                    found.refreshPositionAndAngles(
                            spawn.getX() + 0.5,
                            spawn.getY(),
                            spawn.getZ() + 0.5,
                            player.getYaw(),
                            0
                    );
                    return;
                }


                // ======================
                // 別ディメンション
                // ======================
                StudentWorldState.StudentData data = state.getData(sid);
                if (data == null) return;

                var key = net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.WORLD,
                        new Identifier(data.dimension)
                );

                ServerWorld oldWorld = player.getServer().getWorld(key);
                if (oldWorld == null) return;

                Entity other = oldWorld.getEntity(uuid);
                if (other == null || !other.isAlive()) return;

                Entity moved = other.moveToWorld(sw);

                if (moved != null) {
                    moved.refreshPositionAndAngles(
                            spawn.getX() + 0.5,
                            spawn.getY(),
                            spawn.getZ() + 0.5,
                            player.getYaw(),
                            0
                    );
                }
            });
        });
    }
    // =========================
// StudentId 文字列 → enum 変換
// =========================
    private static StudentId parseStudentId(String s) {
        for (StudentId id : StudentId.values()) {
            if (id.asString().equalsIgnoreCase(s)) return id;
            if (id.name().equalsIgnoreCase(s)) return id;
        }
        throw new IllegalArgumentException("Unknown StudentId: " + s);
    }


}
