package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.inventory.StudentInventory;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import com.licht_meilleur.blue_student.student.StudentLifeState;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

public abstract class AbstractStudentEntity extends PathAwareEntity implements IStudentEntity, GeoEntity {

    // ===== あなたのjson命名（記憶している規約）=====
    public static final String ANIM_IDLE   = "animation.model.idle";
    public static final String ANIM_RUN    = "animation.model.run";
    public static final String ANIM_SHOT   = "animation.model.shot";
    public static final String ANIM_RELOAD = "animation.model.reload";
    public static final String ANIM_SLEEP  = "animation.model.sleep";

    // ===== 共通：演出トリガー（全生徒共通）=====
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> RELOAD_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // ===== GeckoLib =====
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop(ANIM_RUN);
    private static final RawAnimation SHOT   = RawAnimation.begin().thenPlay(ANIM_SHOT);
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay(ANIM_RELOAD);

    // client演出タイマー
    private int clientShotTicks = 0;
    private int lastShotTrigger = 0;
    private boolean shotJustStarted = false;

    private int clientReloadTicks = 0;
    private int lastReloadTrigger = 0;
    private boolean reloadJustStarted = false;

    // shotは固定（後で WeaponSpec に入れてもOK）
    private static final int SHOT_ANIM_TICKS = 12;

    // ===== Life / State =====
    protected StudentLifeState lifeState = StudentLifeState.NORMAL;
    protected int recoverTick = 0;

    protected BlockPos securityPos;
    protected UUID ownerUuid = null;

    // ===== Inventory =====
    protected final StudentInventory studentInventory = new StudentInventory(9, this::onStudentInventoryChanged);

    private boolean appliedStats = false;

    // ===== Ammo / Reload =====
    protected int ammoInMag = 0;
    protected int reloadTicksLeft = 0;
    protected boolean ammoInitDone = false;

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
                .add(EntityAttributes.GENERIC_ARMOR, 0.0);
    }

    protected AbstractStudentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);

        this.setStepHeight(1.0f);

        // 経路探索で危険を避ける
        this.setPathfindingPenalty(PathNodeType.LAVA, 80.0f);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 40.0f);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 20.0f);
    }

    // ===== 演出フック（必要なら個別で上書き）=====
    @Override
    public void requestShot() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(SHOT_TRIGGER, this.dataTracker.get(SHOT_TRIGGER) + 1);
    }

    @Override
    public void requestReload() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(RELOAD_TRIGGER, this.dataTracker.get(RELOAD_TRIGGER) + 1);
    }

    // ===== 必須：生徒ID =====
    @Override public abstract StudentId getStudentId();

    // ===== AI mode tracked data は各生徒クラスで登録して返す =====
    protected abstract TrackedData<StudentAiMode> getAiModeTrackedData();

    // ===== allowed/default =====
    protected EnumSet<StudentAiMode> getAllowedAiModes() {
        StudentAiMode[] allowed = getStudentId().getAllowedAis();
        EnumSet<StudentAiMode> set = EnumSet.noneOf(StudentAiMode.class);
        if (allowed != null) for (StudentAiMode m : allowed) set.add(m);
        if (set.isEmpty()) set.add(StudentAiMode.FOLLOW);
        return set;
    }

    protected StudentAiMode getDefaultAiMode() {
        StudentAiMode[] allowed = getStudentId().getAllowedAis();
        return (allowed != null && allowed.length > 0) ? allowed[0] : StudentAiMode.FOLLOW;
    }

    // ===== security =====
    @Override public BlockPos getSecurityPos() { return securityPos; }
    @Override public void setSecurityPos(BlockPos pos) { this.securityPos = pos; }

    // ===== owner =====
    @Override public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; onStudentInventoryChanged(); }
    @Override public UUID getOwnerUuid() { return ownerUuid; }

    // ===== AI mode =====
    @Override public StudentAiMode getAiMode() { return this.dataTracker.get(getAiModeTrackedData()); }
    @Override public void setAiMode(StudentAiMode mode) {
        if (!getAllowedAiModes().contains(mode)) return;
        this.dataTracker.set(getAiModeTrackedData(), mode);
    }

    // ===== inventory =====
    @Override public Inventory getStudentInventory() { return studentInventory; }
    protected void onStudentInventoryChanged() { }

    // ===== UI =====
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        else if (!ownerUuid.equals(player.getUuid())) return ActionResult.PASS;

        if (player instanceof ServerPlayerEntity sp) {
            openStudentCard(sp);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    protected void openStudentCard(ServerPlayerEntity sp) {
        sp.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                buf.writeInt(((Entity) AbstractStudentEntity.this).getId());
            }
            @Override public Text getDisplayName() { return Text.empty(); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new StudentScreenHandler(syncId, inv, AbstractStudentEntity.this);
            }
        });
    }

    // ===== tick =====
    @Override
    public void tick() {
        super.tick();

        // ===== client：アニメトリガーを見る（描画だけ）=====
        if (this.getWorld().isClient) {
            int shotTrig = this.dataTracker.get(SHOT_TRIGGER);
            if (shotTrig != lastShotTrigger) {
                lastShotTrigger = shotTrig;
                clientShotTicks = SHOT_ANIM_TICKS;
                shotJustStarted = true;
            } else if (clientShotTicks > 0) {
                clientShotTicks--;
            }

            int reloadTrig = this.dataTracker.get(RELOAD_TRIGGER);
            if (reloadTrig != lastReloadTrigger) {
                lastReloadTrigger = reloadTrig;
                WeaponSpec spec = WeaponSpecs.forStudent(getStudentId());
                clientReloadTicks = Math.max(1, spec.reloadTicks);
                reloadJustStarted = true;
            } else if (clientReloadTicks > 0) {
                clientReloadTicks--;
            }
            return;
        }

        // ===== server：弾数初期化・ステータス適用 =====
        WeaponSpec spec = WeaponSpecs.forStudent(getStudentId());
        if (!ammoInitDone) {
            ammoInMag = spec.magSize;
            ammoInitDone = true;
        }

        if (!appliedStats) {
            appliedStats = true;
            applyStatsFromStudentId();
        }

        if (this.age % 5 == 0) tryPickupNearbyItems();
        tickLifeStateServer();
    }

    private void applyStatsFromStudentId() {
        StudentId id = getStudentId();

        EntityAttributeInstance mh = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (mh != null) mh.setBaseValue(id.getBaseMaxHp());

        EntityAttributeInstance ar = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (ar != null) ar.setBaseValue(id.getBaseDefense());

        this.setHealth(this.getMaxHealth());
    }

    private void tickLifeStateServer() {
        switch (lifeState) {
            case SLEEPING -> {
                recoverTick++;
                if (recoverTick > 20) lifeState = StudentLifeState.RECOVERING;
            }
            case RECOVERING -> {
                if (this.age % 10 == 0) this.heal(1f);
                if (this.getHealth() >= this.getMaxHealth()) {
                    lifeState = StudentLifeState.NORMAL;
                    recoverTick = 0;
                    this.setNoGravity(false);

                    BlockPos bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());
                    if (bed != null) {
                        var be = this.getWorld().getBlockEntity(bed);
                        if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(false);
                    }
                }
            }
            default -> {}
        }
    }

    protected void tryPickupNearbyItems() {
        Box box = this.getBoundingBox().expand(2.5);
        var items = this.getWorld().getEntitiesByClass(ItemEntity.class, box, it ->
                it.isAlive() && !it.getStack().isEmpty() && this.squaredDistanceTo(it) < 4.0
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

        for (int i = 0; i < studentInventory.size(); i++) {
            if (studentInventory.getStack(i).isEmpty()) {
                studentInventory.setStack(i, stack);
                studentInventory.markDirty();
                return ItemStack.EMPTY;
            }
        }

        studentInventory.markDirty();
        return stack;
    }

    // ===== NBT =====
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
        nbt.putInt("Ammo", ammoInMag);
        nbt.putInt("ReloadLeft", reloadTicksLeft);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;

        int modeId = nbt.contains("AiMode") ? nbt.getInt("AiMode") : getDefaultAiMode().id;
        this.dataTracker.set(getAiModeTrackedData(), StudentAiMode.fromId(modeId));

        if (nbt.contains("SecX")) {
            securityPos = new BlockPos(nbt.getInt("SecX"), nbt.getInt("SecY"), nbt.getInt("SecZ"));
        } else securityPos = null;

        if (nbt.contains("StudentInv")) studentInventory.readNbt(nbt.getCompound("StudentInv"));

        ammoInMag = nbt.contains("Ammo") ? nbt.getInt("Ammo") : ammoInMag;
        reloadTicksLeft = nbt.contains("ReloadLeft") ? nbt.getInt("ReloadLeft") : 0;

        appliedStats = false;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();

        // AI mode
        this.dataTracker.startTracking(getAiModeTrackedData(), getDefaultAiMode());

        // 演出トリガー（共通）
        this.dataTracker.startTracking(SHOT_TRIGGER, 0);
        this.dataTracker.startTracking(RELOAD_TRIGGER, 0);
    }

    // ===== death =====
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);

        if (this.getWorld().isClient) return;
        ServerWorld sw = (ServerWorld) this.getWorld();

        BlockPos bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());

        if (bed == null) {
            StudentWorldState.get(sw).clearStudent();
            this.discard();
            return;
        }

        this.setHealth(1f);
        this.refreshPositionAndAngles(bed.getX() + 0.5, bed.getY() + 0.2, bed.getZ() + 0.5, 0, 0);
        this.setNoGravity(true);
        this.setAiMode(StudentAiMode.SECURITY);
        this.lifeState = StudentLifeState.SLEEPING;

        var be = sw.getBlockEntity(bed);
        if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(true);
    }

    // ===== ammo api =====
    @Override public int getAmmoInMag() { return ammoInMag; }

    @Override
    public void consumeAmmo(int amount) {
        if (amount <= 0) return;
        ammoInMag = Math.max(0, ammoInMag - amount);
    }

    @Override public boolean isReloading() { return reloadTicksLeft > 0; }

    @Override
    public void startReload(WeaponSpec spec) {
        if (spec.reloadTicks <= 0) return;
        if (isReloading()) return;
        if (spec.infiniteAmmo) return;

        reloadTicksLeft = spec.reloadTicks;
        requestReload();
    }

    @Override
    public void tickReload(WeaponSpec spec) {
        if (!isReloading()) return;

        reloadTicksLeft--;
        if (reloadTicksLeft <= 0) {
            reloadTicksLeft = 0;
            ammoInMag = spec.magSize;
        }
    }

    // ===== GeckoLib controllers =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::commonPredicate));
    }

    private PlayState commonPredicate(AnimationState<AbstractStudentEntity> state) {
        // shot最優先
        if (clientShotTicks > 0) {
            if (shotJustStarted) {
                state.getController().forceAnimationReset();
                shotJustStarted = false;
            }
            state.getController().setAnimation(SHOT);
            return PlayState.CONTINUE;
        }

        // reload次点
        if (clientReloadTicks > 0) {
            if (reloadJustStarted) {
                state.getController().forceAnimationReset();
                reloadJustStarted = false;
            }
            state.getController().setAnimation(RELOAD);
            return PlayState.CONTINUE;
        }

        // 通常
        if (state.isMoving()) {
            state.getController().setAnimation(RUN);
            return PlayState.CONTINUE;
        }
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ===== muzzle近似（サーバー用）=====
    // 重要：サーバーはボーンが取れないので「視線 + 右方向オフセット」で近似する
    public Vec3d getMuzzlePosApprox() {
        Vec3d eye = this.getEyePos();
        Vec3d look = this.getRotationVec(1.0f).normalize();

        // 右方向（look × up）
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(up).normalize();

        // 体格で微調整：前0.6m + 右0.18m + 少し下
        return eye.add(look.multiply(0.60)).add(right.multiply(0.18)).add(0, -0.10, 0);
    }
}
