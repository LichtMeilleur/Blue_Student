package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.StudentFollowGoal;
import com.licht_meilleur.blue_student.ai.StudentPickupItemGoal;
import com.licht_meilleur.blue_student.ai.StudentSecurityGoal;
import com.licht_meilleur.blue_student.inventory.StudentInventory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;

import java.util.UUID;

public class ShirokoEntity extends PathAwareEntity implements GeoEntity {



    // 警備地点（security modeの基準）
    private BlockPos securityPos = null;

    // 所有者（追従対象）
    private UUID ownerUuid = null;

    private static final TrackedData<Integer> AI_MODE =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);
    // =========
    // Inventory (9 slots)
    // =========
    // Inventory (9 slots)
    private final StudentInventory studentInventory = new StudentInventory(9, this::onStudentInventoryChanged);

    private void onStudentInventoryChanged() {
        // NBT保存は writeCustomDataToNbt/readCustomDataFromNbt で行われるので
        // ここで “保存” する必要は基本なし。
        // 「画面が開いている間の同期」は ScreenHandler がやる。

        // もしサーバー側で何か更新フラグを立てたいならここ。
        // 例：見た目更新や移動同期が必要なら
        // this.velocityDirty = true;
    }

    public StudentInventory getStudentInventory() {
        return studentInventory;
    }

    // =========
    // GeckoLib
    // =========
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("animation.model.run");

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
    // Owner
    // =========
    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        onStudentInventoryChanged();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /** UUID優先でowner取得。未設定なら近いプレイヤーを返す（デバッグ用フォールバック） */
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
    // Right click: Follow / Security toggle
    // =========
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        // owner制限したいならON（今は「owner未設定なら初回に所有者にする」運用が楽）
        if (ownerUuid == null) {
            setOwnerUuid(player.getUuid());
        } else if (!ownerUuid.equals(player.getUuid())) {
            return ActionResult.PASS;
        }

        if (player.isSneaking()) {
            // 警備モード：この場を警備地点に
            setAiMode(1);
            setSecurityPos(this.getBlockPos());
            player.sendMessage(Text.literal("Security here."), false);
        } else {
            // 追従モード
            setAiMode(0);
            player.sendMessage(Text.literal("Follow."), false);
        }

        return ActionResult.CONSUME;
    }

    // =========
    // Tick: pickup (軽め)
    // =========
    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;

        // 拾う処理は少し頻度落とす（5tickごと）
        if (this.age % 5 == 0) {
            tryPickupNearbyItems();
        }
    }

    // =========
    // Pickup nearby ItemEntity into studentInventory (9 slots)
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

            if (remain.isEmpty()) {
                it.discard();
            } else {
                it.setStack(remain);
            }

            // 1回に拾うのは1つだけ（暴走防止）
            break;
        }
    }

    private ItemStack insertIntoStudentInventory(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // まず合体
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

        // 次に空きへ
        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack cur = studentInventory.getStack(i);
            if (cur.isEmpty()) {
                studentInventory.setStack(i, stack);
                return ItemStack.EMPTY;
            }
        }

        return stack; // 入らなかった残り
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
        controllers.add(new AnimationController<>(this, "main", 5, this::predicate));
    }

    private <E extends ShirokoEntity> PlayState predicate(final AnimationState<E> state) {
        if (state.isMoving()) {
            return state.setAndContinue(RUN);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // 任意：死亡時にインベントリを落とす（今はOFF推奨：UI確認が先）
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);

        // 必要ならON
        /*
        if (!this.getWorld().isClient) {
            for (int i = 0; i < studentInventory.size(); i++) {
                ItemStack st = studentInventory.getStack(i);
                if (!st.isEmpty()) {
                    this.dropStack(st);
                    studentInventory.setStack(i, ItemStack.EMPTY);
                }
            }
        }
        */
    }
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AI_MODE, 0);
    }
}