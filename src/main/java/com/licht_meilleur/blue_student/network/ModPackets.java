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
    public static final Identifier S2C_SHOT_FX = BlueStudentMod.id("s2c_shot_fx");
    // ★ 64にしたければ 64 にする
    private static final int COST_DIAMOND = 64;

    public static void registerC2S() {
        System.out.println("[BlueStudent] registerC2S called");
        BlueStudentMod.LOGGER.info("[BlueStudent] registerC2S called");

        // --- 既存：AI切り替え ---
        ServerPlayNetworking.registerGlobalReceiver(SET_AI_MODE, (server, player, handler, buf, responseSender) -> {
            // ★bufはここで読み切る（execute内で読まない！）
            final int entityId = buf.readInt();
            final int modeId = buf.readInt();

            server.execute(() -> {
                var world = player.getWorld();
                var raw = world.getEntityById(entityId);

                if (!(raw instanceof IStudentEntity se)) return;

                if (se.getOwnerUuid() == null) se.setOwnerUuid(player.getUuid());
                if (!player.getUuid().equals(se.getOwnerUuid())) return;

                se.setAiMode(StudentAiMode.fromId(modeId));
            });
        });


        // --- 追加：CALL ---
        ServerPlayNetworking.registerGlobalReceiver(CALL_STUDENT, (server, player, handler, buf, responseSender) -> {

            // ★ bufはここで読み切る（execute内で読まない）
            final String sidStr = buf.readString(64);
            final BlockPos tabletPos = buf.readBlockPos();

            server.execute(() -> {
                ServerWorld sw = player.getServerWorld();
                StudentWorldState state = StudentWorldState.get(sw);


                // ===== sid parse =====
                final StudentId sid;
                try {
                    sid = parseStudentId(sidStr);
                } catch (Exception ex) {
                    player.sendMessage(Text.literal("Unknown student: " + sidStr), false);
                    return;
                }


                // ===== 重複チェック（各生徒IDで世界唯一）=====
                if (state.hasStudent(sid)) {
                    player.sendMessage(Text.literal("Already summoned: " + sid.asString()), false);
                    return;
                }

                // ===== コスト =====
                if (!player.getAbilities().creativeMode) {
                    if (!consumeItem(player, Items.DIAMOND, COST_DIAMOND)) {
                        player.sendMessage(Text.literal("Need diamond x" + COST_DIAMOND), false);
                        return;
                    }
                }



                // ===== spawn entity (sidで切替) =====
                Entity raw = switch (sid) {
                    case SHIROKO -> BlueStudentMod.SHIROKO.create(sw);

                    // ↓ 他生徒を作ったらここを差し替える
                     case HOSHINO -> BlueStudentMod.HOSHINO.create(sw);
                     case HINA    -> BlueStudentMod.HINA.create(sw);
                     case ALICE   -> BlueStudentMod.ALICE.create(sw);
                     case KISAKI  -> BlueStudentMod.KISAKI.create(sw);

                    default -> BlueStudentMod.SHIROKO.create(sw); // 保険（enum増えた時）
                };

                if (!(raw instanceof IStudentEntity se)) {
                    player.sendMessage(Text.literal("Spawn failed for " + sid.asString()), false);
                    return;
                }

                Entity e = (Entity) raw;

                // ===== spawn position =====
                BlockPos spawn = tabletPos.up(); // タブレットの1個上
                e.refreshPositionAndAngles(
                        spawn.getX() + 0.5,
                        spawn.getY(),
                        spawn.getZ() + 0.5,
                        player.getYaw(),
                        0
                );

                // owner
                se.setOwnerUuid(player.getUuid());

                // spawn
                sw.spawnEntity(e);

                // 世界唯一としてUUID保存
                state.setStudent(sid, e.getUuid());


                player.sendMessage(Text.literal("Summoned: " + sid.asString()), false);
            });
        });
    }


        private static int countItem(ServerPlayerEntity player, Item item) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) total += st.getCount();
        }
        return total;
    }
    // =========================
    // helpers
    // =========================

    private static StudentId parseStudentId(String s) {
        for (StudentId id : StudentId.values()) {
            if (id.asString().equalsIgnoreCase(s)) return id;     // "shiroko"
            if (id.name().equalsIgnoreCase(s)) return id;         // "SHIROKO"
        }
        throw new IllegalArgumentException("Unknown StudentId: " + s);
    }



    /**
     * インベントリから item を count 個消費する（足りないなら false）
     */
    private static boolean consumeItem(ServerPlayerEntity player, Item item, int count) {
        if (count <= 0) return true;

        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) total += st.getCount();
        }
        if (total < count) return false;

        int remaining = count;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isOf(item)) continue;

            int dec = Math.min(st.getCount(), remaining);
            st.decrement(dec);
            remaining -= dec;
        }

        // ★ サーバー側で確実に反映させる
        inv.markDirty();
        player.playerScreenHandler.sendContentUpdates();

        return true;
    }
}
