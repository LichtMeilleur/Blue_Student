package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.item.Items;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public abstract class AbstractStudentEntity extends PathAwareEntity implements IStudentEntity, GeoEntity {

    // ===== json命名規約 =====
    public static final String ANIM_IDLE   = "animation.model.idle";
    public static final String ANIM_RUN    = "animation.model.run";
    public static final String ANIM_SHOT   = "animation.model.shot";
    public static final String ANIM_RELOAD = "animation.model.reload";
    public static final String ANIM_SLEEP  = "animation.model.sleep";
    public static final String ANIM_JUMP   = "animation.model.jump";
    public static final String ANIM_DODGE  = "animation.model.dodge";
    public static final String ANIM_SWIM   = "animation.model.swim";
    public static final String ANIM_SIT    = "animation.model.sit";
    public static final String ANIM_FALL   = "animation.model.fall";
    public static final String ANIM_EXIT   = "animation.model.exit";
    public static final String ANIM_ACTION = "animation.model.action";

    // ===== 共通：演出トリガー =====
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> RELOAD_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DODGE_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // ★追加：食べるアクション（ACTION再生用）
    private static final TrackedData<Integer> EAT_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Float> AIM_YAW =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> AIM_PITCH =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Integer> LIFE_STATE =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // オーナー不在の間だけ強制セキュリティにしたかどうか
    private boolean forcedSecurityBecauseOwnerOffline = false;

    // ===== GeckoLib =====
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop(ANIM_RUN);
    private static final RawAnimation SHOT   = RawAnimation.begin().thenPlay(ANIM_SHOT);
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay(ANIM_RELOAD);
    private static final RawAnimation SLEEP  = RawAnimation.begin().thenLoop(ANIM_SLEEP);
    private static final RawAnimation EXIT   = RawAnimation.begin().thenPlay(ANIM_EXIT);
    private static final RawAnimation DODGE  = RawAnimation.begin().thenPlay(ANIM_DODGE);
    private static final RawAnimation SWIM   = RawAnimation.begin().thenLoop(ANIM_SWIM);
    private static final RawAnimation SIT    = RawAnimation.begin().thenPlay(ANIM_SIT);
    private static final RawAnimation JUMP   = RawAnimation.begin().thenPlay(ANIM_JUMP);
    private static final RawAnimation FALL   = RawAnimation.begin().thenLoop(ANIM_FALL);
    private static final RawAnimation ACTION = RawAnimation.begin().thenPlay(ANIM_ACTION);

    // client演出タイマー
    private int clientShotTicks = 0;
    private int lastShotTrigger = 0;
    private boolean shotJustStarted = false;

    private int clientReloadTicks = 0;
    private int lastReloadTrigger = 0;
    private boolean reloadJustStarted = false;

    private int clientDodgeTicks = 0;
    private int lastDodgeTrigger = 0;
    private boolean dodgeJustStarted = false;

    private int clientJumpTicks = 0;
    private boolean wasOnGroundClient = true;

    // ★追加：eat演出
    private int clientEatTicks = 0;
    private int lastEatTrigger = 0;
    private boolean eatJustStarted = false;

    private static final int SHOT_ANIM_TICKS = 12;
    private static final int DODGE_ANIM_TICKS = 10;
    private static final int JUMP_ANIM_TICKS  = 8;
    private static final int EAT_ANIM_TICKS   = 16; // actionの見える長さ（好みで）

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

    private UUID queuedFireTargetUuid = null;
    private int lifeTimer;

    private BlockPos respawnBedFoot;
    private BlockPos respawnSafePos;

    // ===== ゴースト =====
    private boolean ghost = false;


    // ★食べてる最中の表示用（Rendererで右手表示に使う）
    // -1 なら非表示
    private int eatingSlot = -1;
    private int eatingServerTicks = 0;



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

        this.setPathfindingPenalty(PathNodeType.LAVA, 80.0f);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 40.0f);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 20.0f);
    }

    // ===== 必須：生徒ID =====
    @Override public abstract StudentId getStudentId();

    // ===== AI mode tracked data は各生徒クラスで登録して返す =====
    protected abstract TrackedData<StudentAiMode> getAiModeTrackedData();

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

    // ===== owner =====
    @Override public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; onStudentInventoryChanged(); }
    @Override public UUID getOwnerUuid() { return ownerUuid; }

    public PlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        return this.getWorld().getPlayerByUuid(ownerUuid);
    }

    // ===== AI mode =====
    @Override public StudentAiMode getAiMode() { return this.dataTracker.get(getAiModeTrackedData()); }
    @Override public void setAiMode(StudentAiMode mode) {
        if (!getAllowedAiModes().contains(mode)) return;
        this.dataTracker.set(getAiModeTrackedData(), mode);
    }

    // ===== inventory =====
    @Override public Inventory getStudentInventory() { return studentInventory; }
    protected void onStudentInventoryChanged() { }

    // ===== 演出トリガー =====
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

    public void requestDodge() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(DODGE_TRIGGER, this.dataTracker.get(DODGE_TRIGGER) + 1);
    }

    // ★追加：食べるアクション
    private void requestEat() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(EAT_TRIGGER, this.dataTracker.get(EAT_TRIGGER) + 1);
    }

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

    // ===== sleep position helpers =====
    protected double getSleepForwardOffset() { return -0.7; }
    protected double getSleepSideOffset() { return 0.0; }
    protected double getSleepYOffset() { return 0.3; }

    protected Vec3d getSleepPos(ServerWorld sw, BlockPos bedFoot) {
        BlockState st = sw.getBlockState(bedFoot);
        if (!(st.getBlock() instanceof OnlyBedBlock) || !st.contains(OnlyBedBlock.FACING)) {
            return new Vec3d(bedFoot.getX() + 0.5, bedFoot.getY() + getSleepYOffset(), bedFoot.getZ() + 0.5);
        }

        Direction dir = st.get(OnlyBedBlock.FACING);

        Vec3d fwd = new Vec3d(dir.getOffsetX(), 0, dir.getOffsetZ()).normalize();
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);

        Vec3d base = new Vec3d(bedFoot.getX() + 0.5, bedFoot.getY() + getSleepYOffset(), bedFoot.getZ() + 0.5);

        return base
                .add(fwd.multiply(getSleepForwardOffset()))
                .add(right.multiply(getSleepSideOffset()));
    }

    // ===== tick =====
    @Override
    public void tick() {
        super.tick();

        // ===== client：トリガーで演出タイマーを回す =====
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

            int dodgeTrig = this.dataTracker.get(DODGE_TRIGGER);
            if (dodgeTrig != lastDodgeTrigger) {
                lastDodgeTrigger = dodgeTrig;
                clientDodgeTicks = DODGE_ANIM_TICKS;
                dodgeJustStarted = true;
            } else if (clientDodgeTicks > 0) {
                clientDodgeTicks--;
            }

            int eatTrig = this.dataTracker.get(EAT_TRIGGER);
            if (eatTrig != lastEatTrigger) {
                lastEatTrigger = eatTrig;
                clientEatTicks = EAT_ANIM_TICKS;
                eatJustStarted = true;
            } else if (clientEatTicks > 0) {
                clientEatTicks--;
            }

            boolean onGroundNow = this.isOnGround();
            if (wasOnGroundClient && !onGroundNow) {
                if (this.getVelocity().y > 0.02) {
                    clientJumpTicks = JUMP_ANIM_TICKS;
                }
            }
            wasOnGroundClient = onGroundNow;
            if (clientJumpTicks > 0) clientJumpTicks--;

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


// 表示タイマー
        if (eatingServerTicks > 0) {
            eatingServerTicks--;
            if (eatingServerTicks <= 0) eatingSlot = -1;
        }


        // ===== server：オーナー不在ならSECURITYにする =====
        if (ownerUuid != null) {
            boolean ownerOnline = (getOwnerPlayer() != null);

            boolean lifeLocksAi = isLifeLocked();

            if (!lifeLocksAi) {
                if (!ownerOnline) {
                    if (!forcedSecurityBecauseOwnerOffline) {
                        forcedSecurityBecauseOwnerOffline = true;
                        this.setAiMode(StudentAiMode.SECURITY);
                        if (this.securityPos == null) this.securityPos = this.getBlockPos();
                    }
                } else {
                    if (forcedSecurityBecauseOwnerOffline) {
                        forcedSecurityBecauseOwnerOffline = false;
                        this.setAiMode(StudentAiMode.FOLLOW);
                    }
                }
            }
        }

    }

    private void applyStatsFromStudentId() {
        StudentId id = getStudentId();

        EntityAttributeInstance mh = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (mh != null) mh.setBaseValue(id.getBaseMaxHp());

        EntityAttributeInstance ar = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (ar != null) ar.setBaseValue(id.getBaseDefense());

        this.setHealth(this.getMaxHealth());
    }

    private boolean isLifeLocked() {
        return lifeState == StudentLifeState.EXITING
                || lifeState == StudentLifeState.RESPAWN_DELAY
                || lifeState == StudentLifeState.WARPING_TO_BED
                || lifeState == StudentLifeState.SLEEPING
                || lifeState == StudentLifeState.RECOVERING;
    }

    // ===== 強制起床（事故フラグ解除を必ずここでやる）=====
    private void forceWakeUp(ServerWorld sw, @Nullable BlockPos fallbackPos, boolean tryTurnOffBedAnim) {
        if (tryTurnOffBedAnim && respawnBedFoot != null) {
            var be = sw.getBlockEntity(respawnBedFoot);
            if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(false);
        }

        BlockPos out = (respawnSafePos != null) ? respawnSafePos
                : (fallbackPos != null ? fallbackPos : this.getBlockPos());

        this.refreshPositionAndAngles(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, this.getYaw(), this.getPitch());
        this.setVelocity(0, 0, 0);
        this.getNavigation().stop();

        // ★事故フラグ解除
        this.setGhost(false);
        this.setAiDisabled(false);
        this.setNoGravity(false);
        this.setInvulnerable(false);

        // 状態クリア
        setLifeState(StudentLifeState.NORMAL);
        lifeTimer = 0;
        respawnBedFoot = null;
        respawnSafePos = null;
    }


    private void tickLifeStateServer() {


        // ★復活ロック中は常に「非衝突・無重力・AI停止」を維持（何かの拍子に戻るのを防ぐ）
        this.setAiDisabled(true);
        this.setNoGravity(true);
        this.setGhost(true);        // noClip



        // NORMALは何もしない
        //if (lifeState == StudentLifeState.NORMAL) return;

        ServerWorld sw = (ServerWorld) this.getWorld();

        // 復活処理中にベッドが壊れたら即復帰（思想通り）
        boolean bedOk = (respawnBedFoot != null && isValidLinkedBed(sw, respawnBedFoot));
        if (!bedOk && isLifeLocked()) {

            System.out.println("[BS] bed invalid! sid=" + getStudentId()
                    + " respawnBedFoot=" + respawnBedFoot
                    + " chunkLoaded=" + sw.isChunkLoaded(respawnBedFoot)
                    + " footState=" + sw.getBlockState(respawnBedFoot));

            BedLinkManager.clearBedPos(ownerUuid, getStudentId()); // ★追加
            forceWakeUp(sw, this.getBlockPos(), true);
            return;
        }


        switch (lifeState) {


            case NORMAL -> {
                // ★ここで必ず復帰保証
                this.setGhost(false);
                this.setAiDisabled(false);
                this.setNoGravity(false);
                this.setInvulnerable(false);
                return;
            }

            // 1) EXITING：exitアニメ待ち
            case EXITING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                if (lifeTimer > 0) {
                    lifeTimer--;
                    return;
                }

                // EXITが終わったら「居ない時間」へ（当たり判定OFFのまま）
                setLifeState(StudentLifeState.RESPAWN_DELAY);
                lifeTimer = 10; // 0.5秒くらい（好みで）
            }

            // 2) RESPAWN_DELAY：世界に居ない演出（判定OFFのまま）
            case RESPAWN_DELAY -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                if (lifeTimer > 0) {
                    lifeTimer--;
                    return;
                }

                // 次：ベッドへワープ
                setLifeState(StudentLifeState.WARPING_TO_BED);
            }

            // 3) WARPING_TO_BED：ベッド位置へ移動して寝る
            case WARPING_TO_BED -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                // まずワープ（このtickで位置確定）
                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                // ベッドBE演出ON
                var be = sw.getBlockEntity(respawnBedFoot);
                if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(true);

                // ★次tickでSLEEPINGへ
                setLifeState(StudentLifeState.SLEEPING);
                return;
            }


            // 4) SLEEPING：寝た目の固定（回復はRECOVERINGに分けてもOK）
            case SLEEPING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                // 寝位置固定
                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                // すぐ回復状態へ（分けたいのであれば）
                setLifeState(StudentLifeState.RECOVERING);
                // recoverTick など使うならここで初期化
                // recoverTick = 0;
            }

            // 5) RECOVERING：回復処理
            case RECOVERING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                // 寝位置固定
                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                // 回復（例：30tickごとに+1）
                if (this.age % 30 == 0) {
                    this.heal(1f);
                }

                if (this.getHealth() >= this.getMaxHealth()) {
                    // 満タンなら起床
                    forceWakeUp(sw, this.getBlockPos(), true);
                }
            }



            default -> {
                // もし将来 enum が増えた時の保険
                setLifeState(StudentLifeState.NORMAL);
            }
        }
    }


    // ===== item pickup =====
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

    // ====== ダメージ処理：invulnerableではなく state で弾く ======
    /*@Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return super.damage(source, amount);
        }

        // ★復活フェーズ中は基本ダメージ無効
        if (isLifeLocked()) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            // ベッドが無効なら「強制起床」+ リンク破棄
            if (respawnBedFoot == null || !isValidLinkedBed(sw, respawnBedFoot)) {
                BedLinkManager.clearBedPos(ownerUuid, getStudentId());
                forceWakeUp(sw, this.getBlockPos(), true);
            }

            return false; // ロック中は常に無効
        }


        float after = this.getHealth() - amount;

        // ★致死なら「死なずに復活処理へ」
        if (after <= 0.5f) {
            startBedRespawn((ServerWorld) this.getWorld());
            return false;
        }

        return super.damage(source, amount);
    }*/


    private void startBedRespawn(ServerWorld sw) {
        BlockPos bed = null;
        if (ownerUuid != null) {
            bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());
        }


        if (bed == null) {
            bed = findNearestBedFoot(sw, getStudentId(), this.getBlockPos(), 96);
            if (bed != null && ownerUuid != null) {
                BedLinkManager.setBedPos(ownerUuid, getStudentId(), bed);
            }
        }


        this.respawnBedFoot = null;
        this.respawnSafePos = null;



        // ★リンクがあっても「本当にベッドか」検証。ダメならリンク削除して探索へ
        if (bed != null && !isValidLinkedBed(sw, bed)) {
            BedLinkManager.clearBedPos(ownerUuid, getStudentId());
            bed = null;
        }


        // ★見つからないなら消す（仕様）
        if (bed == null) {
            StudentWorldState.get(sw).clearStudent(getStudentId());
            this.discard();
            return;
        }

        // 参照保存
        this.respawnBedFoot = bed;
        this.respawnSafePos = findSafeRespawnPosNearBed(sw, bed);

        // ★復活準備：停止＆事故フラグ設定（invulnerableは使わない）
        this.setHealth(1f);
        this.getNavigation().stop();
        this.setVelocity(0, 0, 0);

        this.setAiDisabled(true);
        this.setNoGravity(true);
        this.setGhost(true);     // noClip=true（押し出し/詰まり回避）
        this.setInvulnerable(false);

        // ★EXIT演出（あなたの演出トリガー）
        this.requestExit();

        // 状態：EXITING（演出時間）
        setLifeState(StudentLifeState.EXITING);

        // lifeTimerは共通で使う（例：60tick）
        this.lifeTimer = 60;
    }


    // ===== ベッド探索 =====
    protected @Nullable BlockPos findNearestBedFoot(ServerWorld sw, StudentId sid, BlockPos origin, int r) {
        BlockPos best = null;
        double bestD2 = 1e18;

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -4; dy <= 4; dy++) {
            m.set(origin.getX()+dx, origin.getY()+dy, origin.getZ()+dz);
            BlockState st = sw.getBlockState(m);
            if (!(st.getBlock() instanceof OnlyBedBlock)) continue;
            if (st.get(OnlyBedBlock.PART) != BedPart.FOOT) continue;
            if (st.get(OnlyBedBlock.STUDENT) != sid) continue;

            double d2 = m.getSquaredDistance(origin);
            if (d2 < bestD2) { bestD2 = d2; best = m.toImmutable(); }
        }
        return best;
    }

    @Nullable
    private BlockPos findSafeRespawnPosNearBed(ServerWorld world, BlockPos bedFootPos) {
        BlockPos base = bedFootPos.up();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY(), base.getZ() + dz);
                    if (isSafeStandPos(world, m)) {
                        return m.toImmutable();
                    }
                }
            }
        }
        return base;
    }

    private boolean isSafeStandPos(ServerWorld world, BlockPos pos) {
        BlockPos below = pos.down();
        var belowState = world.getBlockState(below);

        if (belowState.isAir()) return false;
        if (belowState.getCollisionShape(world, below).isEmpty()) return false;

        if (!world.getFluidState(below).isEmpty()) return false;

        if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;
        if (!world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) return false;

        return true;
    }

    // ===== ghost =====
    private void setGhost(boolean v) {
        ghost = v;
        this.noClip = v;
    }

    @Override
    public boolean isAttackable() {
        // ★復活ロック中は攻撃対象にしない
        return !isLifeLocked() && super.isAttackable();
    }

    @Override
    public boolean canHit() {
        // ★当たり判定（ヒット判定）自体を無効化したい場合
        // Yarn環境で存在するなら効きます（存在しないならこのoverrideは消してください）
        return !isLifeLocked() && super.canHit();
    }

    @Override
    public boolean canBeHitByProjectile() {
        // ★弾・矢なども無効化
        return !isLifeLocked() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isPushable() {
        // ★押し出し（接触）無効
        return !ghost && !isLifeLocked() && super.isPushable();
    }

    @Override
    public boolean collidesWith(Entity other) {
        // ★衝突無効（あなたの ghost ロジックを強化）
        return !ghost && !isLifeLocked() && super.collidesWith(other);
    }


    private void setLifeState(StudentLifeState s) {
        this.lifeState = s;
        if (!this.getWorld().isClient) {
            this.dataTracker.set(LIFE_STATE, s.ordinal());
        }
    }

    private StudentLifeState getLifeStateClientSafe() {
        if (this.getWorld().isClient) {
            int idx = this.dataTracker.get(LIFE_STATE);
            idx = Math.max(0, Math.min(idx, StudentLifeState.values().length - 1));
            return StudentLifeState.values()[idx];
        }
        return this.lifeState;
    }

    protected float getBedYaw(ServerWorld world, BlockPos bedFoot) {
        BlockState st = world.getBlockState(bedFoot);

        if (!(st.getBlock() instanceof OnlyBedBlock) || !st.contains(OnlyBedBlock.FACING)) {
            return this.getYaw();
        }
        Direction dir = st.get(OnlyBedBlock.FACING);
        return dir.asRotation();
    }

    private boolean isValidLinkedBed(ServerWorld sw, @Nullable BlockPos bedFoot) {
        if (sw == null || bedFoot == null) return false;

        // チャンクロードは例外が出ても false で逃がす（ワールド生成直後や不正座標保険）
        try {
            sw.getChunk(bedFoot);
        } catch (Exception e) {
            return false;
        }

        BlockState foot;
        try {
            foot = sw.getBlockState(bedFoot);
        } catch (Exception e) {
            return false;
        }

        if (!(foot.getBlock() instanceof OnlyBedBlock)) return false;
        if (!foot.contains(OnlyBedBlock.PART) || !foot.contains(OnlyBedBlock.STUDENT) || !foot.contains(OnlyBedBlock.FACING)) return false;
        if (foot.get(OnlyBedBlock.PART) != BedPart.FOOT) return false;
        if (foot.get(OnlyBedBlock.STUDENT) != getStudentId()) return false;

        Direction facing = foot.get(OnlyBedBlock.FACING);
        BlockPos headPos = bedFoot.offset(facing);

        try {
            sw.getChunk(headPos);
        } catch (Exception e) {
            return false;
        }

        BlockState head;
        try {
            head = sw.getBlockState(headPos);
        } catch (Exception e) {
            return false;
        }

        if (!(head.getBlock() instanceof OnlyBedBlock)) return false;
        if (!head.contains(OnlyBedBlock.PART) || !head.contains(OnlyBedBlock.STUDENT) || !head.contains(OnlyBedBlock.FACING)) return false;
        if (head.get(OnlyBedBlock.PART) != BedPart.HEAD) return false;
        if (head.get(OnlyBedBlock.STUDENT) != getStudentId()) return false;
        if (head.get(OnlyBedBlock.FACING) != facing) return false;

        return true;
    }




    private boolean tryEatFromInventory() {
        // すでに食事中なら再開しない
        if (eatingServerTicks > 0) return false;

        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack st = studentInventory.getStack(i);
            if (st.isEmpty()) continue;
            if (!st.isFood()) continue;

            FoodComponent food = st.getItem().getFoodComponent();
            if (food == null) continue;

            // デバフ食品（腐肉など）を除外
            if (isBadFoodItem(st)) continue;


            // 食べる：満腹度加算（簡易）
            int hunger = Math.max(0, food.getHunger());
            float sat = Math.max(0f, food.getSaturationModifier()) * hunger * 2.0f;


            // “食べた瞬間にちょい回復”を入れるならここ（好み）
            // this.heal(Math.max(1f, hunger * 0.5f));

            // スタック消費
            st.decrement(1);
            studentInventory.markDirty();

            // 演出：ACTIONトリガー
            requestEat();

            // 右手表示用
            eatingSlot = i;
            eatingServerTicks = EAT_ANIM_TICKS;

            return true;
        }
        return false;
    }



    // ===== Rendererが参照する（右手表示用）=====
    public int getEatingSlotForRender() {
        return eatingSlot;
    }

    public ItemStack getEatingStackForRender() {
        if (eatingSlot < 0) return ItemStack.EMPTY;
        if (eatingSlot >= studentInventory.size()) return ItemStack.EMPTY;
        return studentInventory.getStack(eatingSlot);
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

    // ===== queue fire =====
    @Override
    public void queueFire(LivingEntity target) {
        if (getWorld().isClient) return;
        if (target == null) return;
        queuedFireTargetUuid = target.getUuid();
    }

    @Override
    public boolean hasQueuedFire() {
        return queuedFireTargetUuid != null;
    }

    @Override
    public LivingEntity consumeQueuedFireTarget() {
        if (getWorld().isClient) return null;
        if (queuedFireTargetUuid == null) return null;

        var w = (ServerWorld) getWorld();
        var e = w.getEntity(queuedFireTargetUuid);
        queuedFireTargetUuid = null;

        return (e instanceof LivingEntity le) ? le : null;
    }

    // ===== GeckoLib controllers =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::commonPredicate));
    }

    private PlayState commonPredicate(AnimationState<AbstractStudentEntity> state) {
        StudentLifeState ls = getLifeStateClientSafe();

        // ① 復活系は最優先（ここでRUNに落ちるのを完全に防ぐ）
        if (ls == StudentLifeState.EXITING) {
            state.getController().setAnimation(EXIT);
            return PlayState.CONTINUE;
        }

        // ★WARP中は “寝る前のワープ準備” なので、とにかく動かない系を当てる
        // ここをIDLEにしておくと一番事故りにくい（EXITでも可）
        if (ls == StudentLifeState.WARPING_TO_BED) {
            state.getController().setAnimation(IDLE); // or EXIT
            return PlayState.CONTINUE;
        }

        // ★「回復中も寝てる」= SLEEPING + RECOVERING は SLEEP固定
        if (ls == StudentLifeState.SLEEPING || ls == StudentLifeState.RECOVERING) {
            state.getController().setAnimation(SLEEP);
            return PlayState.CONTINUE;
        }

        // ② ここから下は通常演出（食事/回避/射撃/リロードなど）
        if (clientEatTicks > 0) {
            if (eatJustStarted) {
                state.getController().forceAnimationReset();
                eatJustStarted = false;
            }
            state.getController().setAnimation(ACTION);
            return PlayState.CONTINUE;
        }

        if (clientDodgeTicks > 0) {
            if (dodgeJustStarted) {
                state.getController().forceAnimationReset();
                dodgeJustStarted = false;
            }
            state.getController().setAnimation(DODGE);
            return PlayState.CONTINUE;
        }

        if (this.hasVehicle()) {
            state.getController().setAnimation(SIT);
            return PlayState.CONTINUE;
        }

        if (this.isTouchingWater()) {
            state.getController().setAnimation(SWIM);
            return PlayState.CONTINUE;
        }

        if (clientJumpTicks > 0) {
            state.getController().setAnimation(JUMP);
            return PlayState.CONTINUE;
        }

        if (!this.isOnGround() && this.getVelocity().y < -0.08) {
            state.getController().setAnimation(FALL);
            return PlayState.CONTINUE;
        }

        if (clientShotTicks > 0) {
            if (shotJustStarted) {
                state.getController().forceAnimationReset();
                shotJustStarted = false;
            }
            state.getController().setAnimation(SHOT);
            return PlayState.CONTINUE;
        }

        if (clientReloadTicks > 0) {
            if (reloadJustStarted) {
                state.getController().forceAnimationReset();
                reloadJustStarted = false;
            }
            state.getController().setAnimation(RELOAD);
            return PlayState.CONTINUE;
        }

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
    public Vec3d getMuzzlePosApprox() {
        Vec3d eye = this.getEyePos();
        Vec3d look = this.getRotationVec(1.0f).normalize();

        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(up).normalize();

        return eye.add(look.multiply(0.60)).add(right.multiply(0.18)).add(0, -0.10, 0);
    }

    @Environment(EnvType.CLIENT)
    private Vec3d clientMuzzleWorldPos = null;

    @Environment(EnvType.CLIENT)
    public void setClientMuzzleWorldPos(Vec3d pos) { this.clientMuzzleWorldPos = pos; }

    @Environment(EnvType.CLIENT)
    public Vec3d getClientMuzzleWorldPosOrApprox() {
        return (clientMuzzleWorldPos != null) ? clientMuzzleWorldPos : getMuzzlePosApprox();
    }

    // ===== movement tweak =====
    @Override
    public void tickMovement() {
        // ★ serverで復活ロック中は「移動処理そのものを潰す」
        if (!this.getWorld().isClient && isLifeLocked()) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            // 念押し：AI/ナビ/速度を殺す
            this.getNavigation().stop();
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;

            // 位置固定が必要な状態だけベッドへ吸着
            if ((lifeState == StudentLifeState.WARPING_TO_BED
                    || lifeState == StudentLifeState.SLEEPING
                    || lifeState == StudentLifeState.RECOVERING)
                    && respawnBedFoot != null) {

                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw);
                this.setBodyYaw(yaw);
                this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());
            }

            // ★ここが重要：super.tickMovement() を呼ばない
            return;
        }

        // ---- 通常時の移動 ----
        super.tickMovement();

        if (this.getWorld().isClient) return;

        // 既存：水中の微加速（必要なら）
        if (this.isTouchingWater() && !this.hasVehicle()) {
            Vec3d look = this.getRotationVec(1.0f);
            Vec3d forward = new Vec3d(look.x, 0, look.z);
            if (forward.lengthSquared() > 1e-6) {
                forward = forward.normalize().multiply(0.03);
                this.addVelocity(forward.x, 0.0, forward.z);
            }
        }
    }


    // ===== aim api =====
    public void setAimAngles(float yaw, float pitch) {
        if (!getWorld().isClient) {
            dataTracker.set(AIM_YAW, yaw);
            dataTracker.set(AIM_PITCH, pitch);
        }
    }
    public float getAimYaw() { return dataTracker.get(AIM_YAW); }
    public float getAimPitch() { return dataTracker.get(AIM_PITCH); }

    // ===== NBT =====
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        nbt.putBoolean("ForcedSecurityOffline", forcedSecurityBecauseOwnerOffline);

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

        forcedSecurityBecauseOwnerOffline = nbt.getBoolean("ForcedSecurityOffline");

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

        // ★超重要：事故フラグ強制解除（復活途中の再開をNBT保存していない前提）
        this.setInvulnerable(false);
        this.setNoGravity(false);
        this.noClip = false;
        this.setAiDisabled(false);
        this.ghost = false;

        this.lifeState = StudentLifeState.NORMAL;
        this.dataTracker.set(LIFE_STATE, StudentLifeState.NORMAL.ordinal());

        this.respawnBedFoot = null;
        this.respawnSafePos = null;
        this.lifeTimer = 0;

        this.eatingSlot = -1;
        this.eatingServerTicks = 0;







    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();

        this.dataTracker.startTracking(LIFE_STATE, StudentLifeState.NORMAL.ordinal());

        this.dataTracker.startTracking(DODGE_TRIGGER, 0);
        this.dataTracker.startTracking(EAT_TRIGGER, 0);

        this.dataTracker.startTracking(AIM_YAW, 0f);
        this.dataTracker.startTracking(AIM_PITCH, 0f);

        this.dataTracker.startTracking(getAiModeTrackedData(), getDefaultAiMode());

        this.dataTracker.startTracking(SHOT_TRIGGER, 0);
        this.dataTracker.startTracking(RELOAD_TRIGGER, 0);



    }

    // ===== remove =====
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.getWorld().isClient) return;

        if (this.getWorld() instanceof ServerWorld sw) {
            StudentWorldState st = StudentWorldState.get(sw);
            var cur = st.getStudentUuid(getStudentId());
            if (cur != null && cur.equals(this.getUuid())) {
                st.clearStudent(getStudentId());
            }
        }
    }

    // ====== ここはあなたの既存実装に合わせて存在している前提（なければ追加して下さい）=====
    // exit演出用（DataTrackerトリガーの実装など）
    public void requestExit() {
        // 既にあなたのコードにある想定：無ければ、EXIT用のTrackedDataを追加してここで++してください
    }

    // ====== 近接アイテム拾い等で使っているなら、必要に応じて残してください =====

    // まずは「危険そうな食べ物は食べない」ブラックリスト方式（確実にコンパイルが通る）
    private boolean isBadFoodItem(ItemStack st) {
        if (st.isEmpty()) return true;

        // 代表的なデバフ・事故系
        if (st.isOf(Items.ROTTEN_FLESH)) return true;      // 腐った肉：空腹
        if (st.isOf(Items.POISONOUS_POTATO)) return true;  // 毒芋
        if (st.isOf(Items.SPIDER_EYE)) return true;        // 毒
        if (st.isOf(Items.PUFFERFISH)) return true;        // 毒・吐き気など

        // 事故りやすい系（好みでON/OFF）
        if (st.isOf(Items.CHORUS_FRUIT)) return true;      // テレポ事故
        if (st.isOf(Items.SUSPICIOUS_STEW)) return true;   // 効果が多様で制御しづらい

        return false;
    }
    private boolean tryEatFoodForHeal() {
        if (eatingServerTicks > 0) return false;

        for (int i = 0; i < studentInventory.size(); i++) {
            ItemStack st = studentInventory.getStack(i);
            if (st.isEmpty()) continue;
            if (!st.isFood()) continue;

            // ★ ブラックリスト（安全確定）
            if (st.isOf(Items.ROTTEN_FLESH)) continue;
            if (st.isOf(Items.POISONOUS_POTATO)) continue;
            if (st.isOf(Items.SPIDER_EYE)) continue;
            if (st.isOf(Items.PUFFERFISH)) continue;

            int heal = 6; // 固定回復量（好みで）

            this.heal(heal);

            st.decrement(1);
            studentInventory.markDirty();

            requestEat();

            eatingSlot = i;
            eatingServerTicks = EAT_ANIM_TICKS;

            return true;
        }
        return false;
    }
    public void faceTargetForShot(LivingEntity target, float maxYawStep, float maxPitchStep) {
        if (target == null) return;

        Vec3d from = this.getEyePos();
        Vec3d to = target.getEyePos();

        Vec3d d = to.subtract(from);

        double dx = d.x;
        double dy = d.y;
        double dz = d.z;

        double horiz = Math.sqrt(dx*dx + dz*dz);

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horiz)));

        float newYaw = approachAngle(this.getYaw(), targetYaw, maxYawStep);
        float newPitch = approachAngle(this.getPitch(), targetPitch, maxPitchStep);

        this.setYaw(newYaw);
        this.setPitch(newPitch);
        this.setHeadYaw(newYaw);
        this.bodyYaw = newYaw;
    }

    private float approachAngle(float cur, float target, float maxStep) {
        float delta = net.minecraft.util.math.MathHelper.wrapDegrees(target - cur);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return cur + delta;
    }
    // ===== security =====
    @Override
    public BlockPos getSecurityPos() { return securityPos; }

    @Override
    public void setSecurityPos(BlockPos pos) { this.securityPos = pos; }

    @Override
    protected void applyDamage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            super.applyDamage(source, amount);
            return;
        }

        // 復活フェーズ中は一切減らさない
        if (isLifeLocked()) return;

        float after = this.getHealth() - amount;

        // ★ここが“致死”の最終判定ポイント
        if (after <= 0.5f) {
            startBedRespawn((ServerWorld) this.getWorld());
            return; // super.applyDamage を呼ばないので死なない
        }

        super.applyDamage(source, amount);
    }

    // Goalから参照するだけの薄い公開API（中身は既存のprivateを呼ぶだけ）
    public boolean isLifeLockedForGoal() {
        return isLifeLocked();
    }

    public void requestEatFromGoal() {
        requestEat();
    }

    public void startEatingVisualFromGoal(int slot, int ticks) {
        this.eatingSlot = slot;
        this.eatingServerTicks = ticks;
    }

    // ブラックリストもGoal側で使えるように
    public boolean isBadFoodItemForGoal(ItemStack st) {
        return isBadFoodItem(st); // 既存のprivateを呼ぶ
    }



}
