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
    public static final Identifier CRAFT_CHAMBER_CRAFT = BlueStudentMod.id("craft_chamber_craft");
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
                try {
                    ServerWorld sw = player.getServerWorld();
                    StudentWorldState state = StudentWorldState.get(sw);

                    StudentId sid = parseStudentId(sidStr);

                    System.out.println("[BlueStudent] CALL start sid=" + sid.asString() + " pos=" + tabletPos
                            + " dim=" + sw.getRegistryKey().getValue());

                    // すでに召喚済みなら終了
                    if (state.hasStudent(sid)) {
                        player.sendMessage(Text.literal("Already summoned"), false);
                        System.out.println("[BlueStudent] CALL blocked by hasStudent sid=" + sid.asString());
                        return;
                    }

                    boolean creative = player.getAbilities().creativeMode;

                    // ★コストチェック（消費はまだしない）
                    if (!creative) {
                        int have = countItem(player, Items.DIAMOND);
                        if (have < COST_DIAMOND) {
                            player.sendMessage(Text.literal("Need " + COST_DIAMOND + " diamonds (have " + have + ")"), false);
                            return;
                        }
                    }

                    // 生徒生成
                    Entity raw = switch (sid) {
                        case SHIROKO -> BlueStudentMod.SHIROKO.create(sw);
                        case HOSHINO -> BlueStudentMod.HOSHINO.create(sw);
                        case HINA    -> BlueStudentMod.HINA.create(sw);
                        case ALICE   -> BlueStudentMod.ALICE.create(sw);
                        case KISAKI  -> BlueStudentMod.KISAKI.create(sw);
                        case MARIE  -> BlueStudentMod.MARIE.create(sw);
                        case HIKARI  -> BlueStudentMod.HIKARI.create(sw);
                        case NOZOMI  -> BlueStudentMod.NOZOMI.create(sw);
                    };

                    if (raw == null) {
                        System.out.println("[BlueStudent] CALL raw==null sid=" + sid.asString());
                        player.sendMessage(Text.literal("Spawn failed (raw==null)"), false);
                        return;
                    }

                    if (!(raw instanceof IStudentEntity se)) {
                        System.out.println("[BlueStudent] CALL raw not IStudentEntity sid=" + sid.asString()
                                + " class=" + raw.getClass().getName());
                        player.sendMessage(Text.literal("Spawn failed (type mismatch)"), false);
                        return;
                    }

                    BlockPos spawn = tabletPos.up();

                    raw.refreshPositionAndAngles(
                            spawn.getX() + 0.5,
                            spawn.getY(),
                            spawn.getZ() + 0.5,
                            player.getYaw(),
                            0
                    );

                    se.setOwnerUuid(player.getUuid());

                    boolean ok = sw.spawnEntity(raw);
                    System.out.println("[BlueStudent] CALL spawnEntity ok=" + ok + " uuid=" + raw.getUuidAsString());

                    if (!ok) {
                        player.sendMessage(Text.literal("Spawn failed"), false);
                        return;
                    }

                    // ★ここでコスト支払い（Ticket優先 → ダイヤ64）
                    if (!consumeSummonCost(player)) {
                        player.sendMessage(Text.literal("Not enough cost. Need 1 Ticket (preferred) or 64 Diamonds."), false);
                        return;
                    }

                    // 位置＋DIM保存
                    state.setStudent(sid, raw.getUuid(), sw, spawn);

                    player.sendMessage(Text.literal("Summoned"), false);

                    System.out.println("[BlueStudent] CALL done sid=" + sid.asString());

                } catch (Throwable t) {
                    System.out.println("[BlueStudent] CALL crashed: " + t);
                    t.printStackTrace();
                    player.sendMessage(Text.literal("CALL crashed. See log."), false);
                }
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
                    state.setStudent(sid, uuid, sw, spawn);

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

// ★復活中はCallBack無効（即復活事故防止）
                if (other instanceof com.licht_meilleur.blue_student.entity.AbstractStudentEntity ase) {
                    if (ase.isLifeLockedForGoal()) {
                        player.sendMessage(Text.literal("Student is respawning..."), false);
                        return;
                    }

                    boolean ok = ase.teleportToWorldForCallback(sw, spawn, player.getYaw());
                    if (ok) {
                        state.setStudent(sid, uuid, sw, spawn); // ★DIM/POS更新
                    }
                    return;
                }

// fallback（基本起きないはず）
                Entity moved = other.moveToWorld(sw);
                if (moved != null) {
                    moved.refreshPositionAndAngles(
                            spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            player.getYaw(), 0
                    );
                    state.setStudent(sid, uuid, sw, spawn);
                }

            });
        });
        // =========================================
// CRAFT CHAMBER (クラフト)
// =========================================
        ServerPlayNetworking.registerGlobalReceiver(CRAFT_CHAMBER_CRAFT, (server, player, handler, buf, responseSender) -> {

            final BlockPos chamberPos = buf.readBlockPos();
            final int pageIndex = buf.readVarInt();

            server.execute(() -> {
                try {
                    ServerWorld sw = player.getServerWorld();

                    // 悪用防止：距離チェック（8ブロック以内）
                    if (player.squaredDistanceTo(chamberPos.getX() + 0.5, chamberPos.getY() + 0.5, chamberPos.getZ() + 0.5) > 64) {
                        return;
                    }

                    var be = sw.getBlockEntity(chamberPos);
                    if (!(be instanceof com.licht_meilleur.blue_student.block.entity.CraftChamberBlockEntity chamber)) {
                        return;
                    }

                    // レシピ取得（あなたが作ったCraftChamberRecipesを使う）
                    var recipe = com.licht_meilleur.blue_student.craft_chamber.CraftChamberRecipes.byIndex(pageIndex);

                    boolean creative = player.getAbilities().creativeMode;

                    if (!creative) {
                        // 1) 足りるかチェック
                        for (var cost : recipe.costs()) {
                            if (countItem(player, cost.item()) < cost.count()) {
                                player.sendMessage(Text.literal("Not enough materials"), true);
                                return;
                            }
                        }
                        // 2) 消費
                        for (var cost : recipe.costs()) {
                            removeItem(player, cost.item(), cost.count());
                        }
                    }

                    // 3) 生成（ブロックからポップ）
                    ItemStack out = recipe.output().copy();

                    var drop = new net.minecraft.entity.ItemEntity(
                            sw,
                            chamberPos.getX() + 0.5,
                            chamberPos.getY() + 1.1,
                            chamberPos.getZ() + 0.5,
                            out
                    );
                    drop.setToDefaultPickupDelay();
                    drop.setVelocity(0, 0.20, 0); // 少し上に跳ねる
                    sw.spawnEntity(drop);

                    // 演出（任意）
                    sw.playSound(null, chamberPos, net.minecraft.sound.SoundEvents.BLOCK_ANVIL_USE,
                            net.minecraft.sound.SoundCategory.BLOCKS, 0.6f, 1.2f);

                } catch (Throwable t) {
                    System.out.println("[BlueStudent] CRAFT_CHAMBER_CRAFT crashed: " + t);
                    t.printStackTrace();
                    player.sendMessage(Text.literal("Craft failed. See log."), false);
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
    private static int countItem(ServerPlayerEntity player, Item item) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    private static void removeItem(ServerPlayerEntity player, Item item, int amount) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;

            int take = Math.min(amount, s.getCount());
            s.decrement(take);
            amount -= take;
        }
    }
    private static boolean consumeSummonCost(ServerPlayerEntity player) {

        // クリエイティブは無料にしたいなら有効化
        if (player.isCreative()) return true;

        // ① Ticket優先
        if (consumeCount(player, BlueStudentMod.TICKET, 1)) {
            return true;
        }

        // ② ダイヤ64
        return consumeCount(player, Items.DIAMOND, COST_DIAMOND);
    }

    private static boolean consumeOne(ServerPlayerEntity player, Item item) {
        return consumeCount(player, item, 1);
    }

    private static boolean consumeCount(ServerPlayerEntity player, Item item, int amount) {
        if (amount <= 0) return true;

        var inv = player.getInventory();

        // まず所持数チェック
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && st.isOf(item)) total += st.getCount();
            if (total >= amount) break;
        }
        if (total < amount) return false;

        // 実際に減らす（前から順に）
        int remain = amount;
        for (int i = 0; i < inv.size() && remain > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !st.isOf(item)) continue;

            int take = Math.min(remain, st.getCount());
            st.decrement(take);
            remain -= take;
        }

        player.currentScreenHandler.sendContentUpdates(); // 念のため同期
        return true;
    }

}
