package com.licht_meilleur.blue_student.network;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.entity.ShirokoEntity;
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

import java.util.UUID;

public class ModPackets {
    public static final Identifier SET_AI_MODE  = BlueStudentMod.id("set_ai_mode");
    public static final Identifier CALL_STUDENT = BlueStudentMod.id("call_student");

    // ★ 64にしたければ 64 にする
    private static final int COST_DIAMOND = 1;

    public static void registerC2S() {

        // --- 既存：AI切り替え ---
        ServerPlayNetworking.registerGlobalReceiver(SET_AI_MODE, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            int mode = buf.readInt();

            server.execute(() -> {
                var world = player.getWorld();
                if (world.getEntityById(entityId) instanceof ShirokoEntity e) {
                    if (e.getOwnerUuid() == null) e.setOwnerUuid(player.getUuid());
                    if (!player.getUuid().equals(e.getOwnerUuid())) return;
                    e.setAiMode(mode);
                }
            });
        });

        // --- 追加：CALL ---
        ServerPlayNetworking.registerGlobalReceiver(CALL_STUDENT, (server, player, handler, buf, responseSender) -> {
            final String sidStr = buf.readString(64);

            server.execute(() -> {
                ServerWorld sw = player.getServerWorld();

                // 0) StudentId パース（"shiroko" でも "SHIROKO" でもOK）
                StudentId sid;
                try {
                    sid = parseStudentId(sidStr);
                } catch (Exception ex) {
                    player.sendMessage(Text.literal("Unknown student: " + sidStr), false);
                    return;
                }

                // いまは ShirokoEntity だけ実装してるので固定
                if (sid != StudentId.SHIROKO) {
                    player.sendMessage(Text.literal("Not implemented yet: " + sid.asString()), false);
                    return;
                }

                // 1) 重複チェック（同じownerのShirokoが既にいるなら召喚しない）
                if (isAlreadySummoned(sw, player.getUuid())) {
                    player.sendMessage(Text.literal("Already summoned."), false);
                    return;
                }

                // 2) ダイヤ消費（クリエは消費しない）
                if (!player.getAbilities().creativeMode) {
                    boolean ok = consumeItem(player, Items.DIAMOND, COST_DIAMOND);
                    if (!ok) {
                        player.sendMessage(Text.literal("Need diamond x" + COST_DIAMOND), false);
                        return;
                    }
                }

                // 3) スポーン（プレイヤーの1ブロック上）
                ShirokoEntity e = BlueStudentMod.SHIROKO.create(sw);
                if (e == null) {
                    player.sendMessage(Text.literal("Spawn failed."), false);
                    return;
                }

                BlockPos spawn = player.getBlockPos().up();
                e.refreshPositionAndAngles(
                        spawn.getX() + 0.5,
                        spawn.getY(),
                        spawn.getZ() + 0.5,
                        player.getYaw(),
                        0
                );

                e.setOwnerUuid(player.getUuid());
                sw.spawnEntity(e);

                player.sendMessage(Text.literal("Summoned: " + sid.asString()), false);
            });
        });
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
     * 同じownerの ShirokoEntity が 1体でも居たら true
     * ※ まずは「重複召喚させない」を最優先でこの仕様にしてます
     */
    private static boolean isAlreadySummoned(ServerWorld sw, UUID owner) {
        // プレイヤー周辺だけだと遠くに居る個体を見落とすので、いったん「ワールド全体に近い」範囲で見る
        // ただし無限は重いので 512 半径くらいにしてます（必要なら増減）
        // 一番確実なのは「UUIDを保存して参照」だけど、それはGUI/登録の段階でやるのが良いです
        for (ShirokoEntity e : sw.getEntitiesByClass(ShirokoEntity.class, new Box(
                -30000000, sw.getBottomY(), -30000000,
                30000000, sw.getTopY(),     30000000
        ), ent -> true)) {
            UUID ou = e.getOwnerUuid();
            if (ou != null && ou.equals(owner)) return true;
        }
        return false;
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
