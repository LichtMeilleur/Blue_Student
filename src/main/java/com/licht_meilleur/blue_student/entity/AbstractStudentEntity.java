
package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.inventory.StudentInventory;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import com.licht_meilleur.blue_student.student.StudentLifeState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;

public abstract class AbstractStudentEntity extends PathAwareEntity implements IStudentEntity {

    protected StudentLifeState lifeState = StudentLifeState.NORMAL;
    protected int recoverTick = 0;
    // =========
    // DataTracker (共通)
    // =========
    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(AbstractStudentEntity.class, StudentAiMode.TRACKED);

    // =========
    // State (共通)
    // =========
    protected BlockPos securityPos;
    protected UUID ownerUuid = null;

    // =========
    // Inventory (共通 9枠)
    // =========
    protected final StudentInventory studentInventory = new StudentInventory(9, this::onStudentInventoryChanged);

    protected AbstractStudentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    // =========
    // 各生徒で返す（必須）
    // =========
    public abstract StudentId getStudentId();

    // =========
    // 生徒ごとの “許可AIモード” と “初期AI”
    // =========
    protected EnumSet<StudentAiMode> getAllowedAiModes() {
        // デフォルト：FOLLOW/SECURITY
        return EnumSet.of(StudentAiMode.FOLLOW, StudentAiMode.SECURITY);
    }

    protected StudentAiMode getDefaultAiMode() {
        return StudentAiMode.FOLLOW;
    }

    public BlockPos getSecurityPos() {
        return securityPos;
    }

    public void setSecurityPos(BlockPos pos) {
        this.securityPos = pos;
    }

    // =========
    // owner
    // =========
    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        onStudentInventoryChanged();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public PlayerEntity getOwnerPlayer() {
        if (this.getWorld() instanceof ServerWorld sw && ownerUuid != null) {
            PlayerEntity p = sw.getPlayerByUuid(ownerUuid);
            if (p != null && p.isAlive()) return p;
        }
        return this.getWorld().getClosestPlayer(this, 16.0);
    }

    // =========
    // AI mode
    // =========
    @Override
    public StudentAiMode getAiMode() {
        return this.dataTracker.get(AI_MODE);
    }

    @Override
    public void setAiMode(StudentAiMode mode) {
        if (!getAllowedAiModes().contains(mode)) return;
        this.dataTracker.set(AI_MODE, mode);
    }

    // =========
    // inventory access
    // =========
    public Inventory getStudentInventory() {
        return studentInventory;
    }

    protected void onStudentInventoryChanged() {
        // ここで同期フックを入れたければ入れる（必要になるまで空でOK）
    }

    // =========
    // 共通：右クリック入口
    // =========
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        // ownerチェック
        if (ownerUuid == null) {
            setOwnerUuid(player.getUuid());
        } else if (!ownerUuid.equals(player.getUuid())) {
            return ActionResult.PASS;
        }

        // まず “StudentCard” を開く（サーバー側）
        // ※ ここを “しゃがみ+空手は別用途” などにしたいなら、overrideで分岐してOK
        if (player instanceof ServerPlayerEntity sp) {
            openStudentCard(sp);
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    /**
     * StudentCard（HandledScreen）を開く
     * 実装はあなたの StudentScreenHandler / ScreenHandlerFactory に合わせる必要があるので、
     * ここは “呼び出し口” として置く。具体的な openHandledScreen 実装は次で一緒に固めよう。
     */
    protected void openStudentCard(ServerPlayerEntity player) {
        // TODO: 次の段階で ScreenHandlerFactory を完成させる
        player.sendMessage(Text.literal("TODO: openStudentCard() not wired yet"), false);
    }

    // =========
    // 共通：tick（拾い物）
    // =========
    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            if (this.age % 5 == 0) tryPickupNearbyItems();
        }
    }

    protected void tryPickupNearbyItems() {
        Box box = this.getBoundingBox().expand(2.5);
        var items = this.getWorld().getEntitiesByClass(ItemEntity.class, box, it ->
                it.isAlive() && !it.getStack().isEmpty() && this.squaredDistanceTo(it) < 2.0 * 2.0
        );

        for (ItemEntity it : items) {
            ItemStack remain = it.getStack().copy();
            remain = insertIntoStudentInventory(remain);

            if (remain.isEmpty()) it.discard();
            else it.setStack(remain);

            break;
        }
    }

    protected ItemStack insertIntoStudentInventory(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // まずスタック合体
        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack cur = studentInventory.getStack(i);
            if (cur.isEmpty()) continue;
            if (!ItemStack.canCombine(cur, stack)) continue;

            int space = cur.getMaxCount() - cur.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, stack.getCount());
            cur.increment(move);
            stack.decrement(move);
            if (stack.isEmpty()) {
                studentInventory.markDirty();
                return ItemStack.EMPTY;
            }
        }

        // 空きに入れる
        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack cur = studentInventory.getStack(i);
            if (cur.isEmpty()) {
                studentInventory.setStack(i, stack);
                studentInventory.markDirty();
                return ItemStack.EMPTY;
            }
        }

        studentInventory.markDirty();
        return stack;
    }

    // =========
    // 共通：NBT
    // =========
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("AiMode", getAiMode().id);

        if (securityPos != null) {
            nbt.putInt("SecX", securityPos.getX());
            nbt.putInt("SecY", securityPos.getY());
            nbt.putInt("SecZ", securityPos.getZ());
        }

        NbtCompound invTag = new NbtCompound();
        studentInventory.writeNbt(invTag);
        nbt.put("StudentInv", invTag);

        nbt.putString("StudentId", getStudentId().asString());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        this.dataTracker.set(AI_MODE, StudentAiMode.fromId(nbt.getInt("AiMode")));

        if (nbt.contains("SecX")) {
            securityPos = new BlockPos(nbt.getInt("SecX"), nbt.getInt("SecY"), nbt.getInt("SecZ"));
        } else {
            securityPos = null;
        }

        if (nbt.contains("StudentInv")) {
            studentInventory.readNbt(nbt.getCompound("StudentInv"));
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AI_MODE, getDefaultAiMode());
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
    }
}
