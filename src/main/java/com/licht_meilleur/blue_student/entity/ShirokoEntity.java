package com.licht_meilleur.blue_student;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class ShirokoEntity extends PathAwareEntity implements GeoEntity {

    // =========
    // Mode
    // =========
    public enum StudentMode {
        FOLLOW, GUARD
    }

    private StudentMode mode = StudentMode.FOLLOW;
    private BlockPos guardPos = null;
    private UUID ownerUuid = null;

    // =========
    // Inventory (27 slots)
    // =========
    private final SimpleInventory inventory = new SimpleInventory(27);

    public SimpleInventory getInventory() {
        return inventory;
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    // =========
    // GeckoLib
    // =========
    // あなたの animation.json の “キー名” に合わせて変えてOK
    // 例: animations側が animation.model.idle なら、ここは "animation.model.idle" を指定する必要があります
    // 今回あなたは "animation.model.tablet_rotation" みたいな方式だったので、同様に合わせています。
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.model.run");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ShirokoEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    // =========
    // Attributes
    // =========
    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0);
    }

    // =========
    // Right click: Follow / Guard toggle
    // =========
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        // 所有者制限（必要ならON）
        if (ownerUuid != null && !ownerUuid.equals(player.getUuid())) {
            return ActionResult.PASS;
        }

        if (player.isSneaking()) {
            mode = StudentMode.GUARD;
            guardPos = this.getBlockPos();
            player.sendMessage(Text.literal("Guard here."), false);
        } else {
            mode = StudentMode.FOLLOW;
            guardPos = null;
            player.sendMessage(Text.literal("Follow."), false);
        }

        return ActionResult.CONSUME;
    }

    // =========
    // Tick: follow/guard + pickup
    // =========
    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // 負荷を抑える（10tickごと）
        if (this.age % 10 == 0) {
            if (mode == StudentMode.FOLLOW) {
                tickFollow(sw);
            } else {
                tickGuard();
            }
        }

        // 拾う処理は少し頻度落とす（5tickごと）
        if (this.age % 5 == 0) {
            tryPickupNearbyItems();
        }
    }

    private void tickFollow(ServerWorld sw) {
        if (ownerUuid == null) return;

        PlayerEntity owner = sw.getPlayerByUuid(ownerUuid);
        if (owner == null) return;

        double d2 = this.squaredDistanceTo(owner);

        // 離れすぎたらテレポ（任意：嫌なら消してOK）
        if (d2 > 16.0 * 16.0) {
            this.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), this.getYaw(), this.getPitch());
            this.getNavigation().stop();
            return;
        }

        // 少し離れてたら追従
        if (d2 > 3.0 * 3.0) {
            this.getNavigation().startMovingTo(owner, 1.15);
        } else {
            this.getNavigation().stop();
        }
    }

    private void tickGuard() {
        if (guardPos == null) return;

        double gx = guardPos.getX() + 0.5;
        double gy = guardPos.getY();
        double gz = guardPos.getZ() + 0.5;

        double d2 = this.squaredDistanceTo(gx, gy, gz);

        // 警戒地点から離れたら戻る
        if (d2 > 2.5 * 2.5) {
            this.getNavigation().startMovingTo(gx, gy, gz, 1.0);
        } else {
            this.getNavigation().stop();
        }
    }

    // =========
    // Pickup nearby ItemEntity into inventory
    // =========
    private void tryPickupNearbyItems() {
        if (this.getWorld().isClient) return;

        Box box = this.getBoundingBox().expand(2.5);
        var items = this.getWorld().getEntitiesByClass(ItemEntity.class, box, it ->
                it.isAlive() && !it.getStack().isEmpty() && this.squaredDistanceTo(it) < 2.0*2.0
        );

        for (ItemEntity it : items) {
            ItemStack remain = it.getStack().copy();
            remain = insertIntoInventory(remain);

            if (remain.isEmpty()) {
                it.discard();
            } else {
                it.setStack(remain);
            }

            // 1回に拾うのは1つだけ（暴走防止）
            break;
        }
    }

    private ItemStack insertIntoInventory(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // まず合体
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack cur = inventory.getStack(i);
            if (cur.isEmpty()) continue;
            if (!ItemStack.canCombine(cur, stack)) continue;

            int space = cur.getMaxCount() - cur.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, stack.getCount());
            cur.increment(move);
            stack.decrement(move);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }

        // 次に空きへ
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack cur = inventory.getStack(i);
            if (cur.isEmpty()) {
                inventory.setStack(i, stack);
                return ItemStack.EMPTY;
            }
        }

        return stack; // 入らなかった残り
    }

    // =========
    // Save / Load (NBT)
    // =========
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putString("Mode", mode.name());
        if (guardPos != null) nbt.putLong("GuardPos", guardPos.asLong());

        // inventory
        NbtList list = new NbtList();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack st = inventory.getStack(i);
            if (!st.isEmpty()) {
                NbtCompound e = new NbtCompound();
                e.putInt("Slot", i);
                e.put("Stack", st.writeNbt(new NbtCompound()));
                list.add(e);
            }
        }
        nbt.put("Inv", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;

        if (nbt.contains("Mode")) {
            try { mode = StudentMode.valueOf(nbt.getString("Mode")); }
            catch (Exception ignored) { mode = StudentMode.FOLLOW; }
        }

        guardPos = nbt.contains("GuardPos") ? BlockPos.fromLong(nbt.getLong("GuardPos")) : null;

        // inventory
        for (int i = 0; i < inventory.size(); i++) inventory.setStack(i, ItemStack.EMPTY);

        NbtList list = nbt.getList("Inv", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            int slot = e.getInt("Slot");
            if (slot >= 0 && slot < inventory.size()) {
                ItemStack st = ItemStack.fromNbt(e.getCompound("Stack"));
                inventory.setStack(slot, st);
            }
        }
    }

    // =========
    // GeckoLib controller
    // =========
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::predicate));
    }

    private <E extends ShirokoEntity> PlayState predicate(final AnimationState<E> state) {
        // moving判定だけのシンプル版
        if (state.isMoving()) {
            return state.setAndContinue(RUN);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // 任意：死亡時に持ち物を落とすかどうか
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);

        // デバッグ：死んだらインベントリを落とす（嫌なら消してOK）
        if (!this.getWorld().isClient) {
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack st = inventory.getStack(i);
                if (!st.isEmpty()) {
                    this.dropStack(st);
                    inventory.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }
}