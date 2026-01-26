package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.StudentFollowGoal;
import com.licht_meilleur.blue_student.ai.StudentSecurityGoal;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.inventory.StudentInventory;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
    // DataTracker
    // =========
    private static final TrackedData<Integer> AI_MODE =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // ★shot を「1回鳴らす」ためのトリガー（カウンタ）。値が変わったらクライアントで再生。
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // =========
    // State
    // =========
    private BlockPos securityPos = null;
    private UUID ownerUuid = null;

    // =========
    // Inventory (9 slots)
    // =========
    private final StudentInventory studentInventory = new StudentInventory(9, this::onStudentInventoryChanged);

    private void onStudentInventoryChanged() {
        // 必要ならフラグ更新など
    }

    public StudentInventory getStudentInventory() {
        return studentInventory;
    }

    // =========
    // GeckoLib
    // =========
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("animation.model.run");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.model.shot");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ★クライアント側だけで回す「shot表示タイマー」
    private int clientShotTicks = 0;
    private int lastShotTrigger = 0;

    private boolean shotJustStarted = false;

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
    // Owner
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
    // AI Mode / Security pos
    // =========
    public int getAiMode() {
        return this.dataTracker.get(AI_MODE);
    }

    public void setAiMode(int mode) {
        this.dataTracker.set(AI_MODE, mode);
    }

    public BlockPos getSecurityPos() {
        return securityPos;
    }

    public void setSecurityPos(BlockPos pos) {
        this.securityPos = pos;
        onStudentInventoryChanged();
    }

    // =========
    // ★shot（モーション確認用）
    // =========
    public void playShotOnce() {
        if (this.getWorld().isClient) return; // サーバーからトリガーする
        int v = this.dataTracker.get(SHOT_TRIGGER);
        this.dataTracker.set(SHOT_TRIGGER, v + 1);
    }

    // =========
    // Right click
    // =========
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        // owner
        if (ownerUuid == null) {
            setOwnerUuid(player.getUuid());
        } else if (!ownerUuid.equals(player.getUuid())) {
            return ActionResult.PASS;
        }

        ItemStack inHand = player.getStackInHand(hand);

        // ★ 空手 + しゃがみ右クリック：ベッド紐づけモード
        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.SHIROKO);
            player.sendMessage(Text.literal("Link mode: SHIROKO. Now sneak+rightclick a vanilla bed."), false);
            return ActionResult.CONSUME;
        }

        // ★デバッグ：手に何も持ってない通常右クリックで shot
        // （不要なら消してOK）
        if (!player.isSneaking() && inHand.isEmpty()) {
            playShotOnce();
            player.sendMessage(Text.literal("Shot!"), false);
            return ActionResult.CONSUME;
        }

        if (player.isSneaking()) {
            setAiMode(1);
            setSecurityPos(this.getBlockPos());
            player.sendMessage(Text.literal("Security here."), false);
        } else {
            setAiMode(0);
            player.sendMessage(Text.literal("Follow."), false);
        }

        return ActionResult.CONSUME;
    }

    // =========
    // Tick
    // =========
    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(SHOT_TRIGGER);
            if (trig != lastShotTrigger) {
                lastShotTrigger = trig;
                clientShotTicks = 12;   // ここは好きに（反動見たいなら少し長め）
                shotJustStarted = true; // ★開始フラグ
            } else if (clientShotTicks > 0) {
                clientShotTicks--;
            }
            return;
        }

        if (this.age % 5 == 0) tryPickupNearbyItems();
    }

    // =========
    // Pickup nearby items
    // =========
    public void tryPickupNearbyItems() {
        if (this.getWorld().isClient) return;

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

    private ItemStack insertIntoStudentInventory(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack cur = studentInventory.getStack(i);
            if (cur.isEmpty()) continue;
            if (!ItemStack.canCombine(cur, stack)) continue;

            int space = cur.getMaxCount() - cur.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, stack.getCount());
            cur.increment(move);
            stack.decrement(move);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }

        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack cur = studentInventory.getStack(i);
            if (cur.isEmpty()) {
                studentInventory.setStack(i, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    // =========
    // Goals
    // =========
    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new StudentFollowGoal(this, 1.1));
        this.goalSelector.add(3, new StudentSecurityGoal(this, 1.0));
    }

    // =========
    // Save / Load (NBT)
    // =========
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("AiMode", getAiMode());

        if (securityPos != null) {
            nbt.putInt("SecX", securityPos.getX());
            nbt.putInt("SecY", securityPos.getY());
            nbt.putInt("SecZ", securityPos.getZ());
        }

        NbtCompound invTag = new NbtCompound();
        studentInventory.writeNbt(invTag);
        nbt.put("StudentInv", invTag);

        // SHOT_TRIGGER は保存しなくてOK（演出用）
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        this.dataTracker.set(AI_MODE, nbt.getInt("AiMode"));

        if (nbt.contains("SecX")) {
            securityPos = new BlockPos(nbt.getInt("SecX"), nbt.getInt("SecY"), nbt.getInt("SecZ"));
        } else {
            securityPos = null;
        }

        if (nbt.contains("StudentInv")) {
            studentInventory.readNbt(nbt.getCompound("StudentInv"));
        }
    }

    // =========
    // GeckoLib controller
    // =========
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // ★transition を 0 にして、補間（ぬるっと）を消す
        controllers.add(new AnimationController<>(this, "main", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ShirokoEntity> state) {
        if (clientShotTicks > 0) {
            if (shotJustStarted) {
                state.getController().forceAnimationReset(); // ★最初の1回だけ
                shotJustStarted = false;
            }
            state.getController().setAnimation(SHOT);
            return PlayState.CONTINUE;
        }

        if (state.isMoving()) return state.setAndContinue(RUN);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AI_MODE, 0);
        this.dataTracker.startTracking(SHOT_TRIGGER, 0);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        // 必要ならドロップ等
    }
}