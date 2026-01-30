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

import java.util.EnumSet;
import java.util.UUID;

public abstract class AbstractStudentEntity extends PathAwareEntity implements IStudentEntity, GeoEntity {

    // ===== あなたのjson命名（記憶している規約）=====
    public static final String ANIM_IDLE   = "animation.model.idle";
    public static final String ANIM_RUN    = "animation.model.run";
    public static final String ANIM_SHOT   = "animation.model.shot";
    public static final String ANIM_RELOAD = "animation.model.reload";
    public static final String ANIM_SLEEP  = "animation.model.sleep";
    public static final String ANIM_JUMP  = "animation.model.jump";
    public static final String ANIM_DODGE  = "animation.model.dodge";
    public static final String ANIM_SWIM  = "animation.model.swim";
    public static final String ANIM_SIT  = "animation.model.sit";
    public static final String ANIM_FALL  = "animation.model.fall";
    public static final String ANIM_EXIT  = "animation.model.exit";

    // ===== 共通：演出トリガー（全生徒共通）=====
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> RELOAD_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);


    private static final TrackedData<Float> AIM_YAW =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> AIM_PITCH =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // オーナー不在の間だけ強制セキュリティにしたかどうか
    private boolean forcedSecurityBecauseOwnerOffline = false;

    // ===== GeckoLib =====
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop(ANIM_RUN);
    private static final RawAnimation SHOT   = RawAnimation.begin().thenPlay(ANIM_SHOT);
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay(ANIM_RELOAD);
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop(ANIM_SLEEP);
    private static final RawAnimation EXIT  = RawAnimation.begin().thenPlay(ANIM_EXIT);
    private static final RawAnimation DODGE   = RawAnimation.begin().thenPlay(ANIM_DODGE);
    private static final RawAnimation SWIM    = RawAnimation.begin().thenLoop(ANIM_SWIM);
    private static final RawAnimation SIT   = RawAnimation.begin().thenPlay(ANIM_SIT);
    private static final RawAnimation Jump = RawAnimation.begin().thenPlay(ANIM_JUMP);
    private static final RawAnimation FALL = RawAnimation.begin().thenLoop(ANIM_FALL);

    // 前回のオフライン時のAIモード（復帰用）
    private StudentAiMode prevAiModeBeforeOffline = null;


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

    private UUID queuedFireTargetUuid = null;
    private int lifeTimer;

    private BlockPos respawnBedFoot; // 保存しておく
    private BlockPos respawnSafePos; // 保存しておく
    private StudentId sid;

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

    private static final TrackedData<Integer> LIFE_STATE =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // ===== extra anim state (client) =====
    private int clientDodgeTicks = 0;
    private int lastDodgeTrigger = 0;
    private boolean dodgeJustStarted = false;

    private int clientJumpTicks = 0;
    private boolean wasOnGroundClient = true;

    // だいたいの長さ（好みで調整）
    private static final int DODGE_ANIM_TICKS = 10;
    private static final int JUMP_ANIM_TICKS  = 8;



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


    //          寝る
    // +: 頭方向へ, -: 足方向へ（値はブロック単位、0.0〜1.0くらいで調整）
    protected double getSleepForwardOffset() { return -0.7; } // 例：頭側へ0.7
    // +: 右へ, -: 左へ（必要なら）
    protected double getSleepSideOffset() { return 0.0; }
    // Y（沈め）
    protected double getSleepYOffset() { return 0.3; }
    protected Vec3d getSleepPos(ServerWorld sw, BlockPos bedFoot) {
        BlockState st = sw.getBlockState(bedFoot);

        // ★ベッドじゃない（壊れた/置換）なら落ちないように fallback
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

    private static final TrackedData<Integer> DODGE_TRIGGER =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);


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


        // ===== server：オーナーのボート連携処理 =====
        if (!getWorld().isClient) {
            var owner = getOwnerPlayer();
            if (owner != null) {
                Entity myV = this.getVehicle();
                Entity ownerV = owner.getVehicle();

                boolean ownerBoat = ownerV instanceof net.minecraft.entity.vehicle.BoatEntity
                        || ownerV instanceof net.minecraft.entity.vehicle.ChestBoatEntity;

                boolean myBoat = myV instanceof net.minecraft.entity.vehicle.BoatEntity
                        || myV instanceof net.minecraft.entity.vehicle.ChestBoatEntity;

                // ① 自分がボートに乗ってるが、オーナーがボートに乗ってない or 別のボートなら降りる
                if (myBoat && (!ownerBoat || ownerV != myV)) {
                    this.stopRiding();
                }

                // （任意）② オーナーがボートに乗ってて、自分が未搭乗＆近いなら搭乗を試す
                // ※これはAI側に任せてもOK。Entity tickでやるなら軽く条件を絞る。
        /*
        if (ownerBoat && myV == null && this.squaredDistanceTo(owner) < 25.0) {
            if (ownerV.getPassengerList().size() < 2) {
                this.startRiding(ownerV, true);
            }
        }
        */
            }
        }


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

                // ===== dodge trigger =====
                int dodgeTrig = this.dataTracker.get(DODGE_TRIGGER);
                if (dodgeTrig != lastDodgeTrigger) {
                    lastDodgeTrigger = dodgeTrig;
                    clientDodgeTicks = DODGE_ANIM_TICKS;
                    dodgeJustStarted = true;
                } else if (clientDodgeTicks > 0) {
                    clientDodgeTicks--;
                }

// ===== jump detect (client) =====
// 「地面→空中」になった瞬間をジャンプ開始として短く再生
                boolean onGroundNow = this.isOnGround();
                if (wasOnGroundClient && !onGroundNow) {
                    // 上向きっぽい時だけ（落下開始では出さない）
                    if (this.getVelocity().y > 0.02) {
                        clientJumpTicks = JUMP_ANIM_TICKS;
                        // 段差を登ったっぽい：1tickでYが増えた（stepHeightで上がった時）
                        double dy = this.getY() - this.prevY;
                        if (dy > 0.20 && this.isOnGround()) {
                            clientJumpTicks = JUMP_ANIM_TICKS;
                        }

                    }
                }
                wasOnGroundClient = onGroundNow;

                if (clientJumpTicks > 0) clientJumpTicks--;

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

        // ===== server：オーナー不在ならSECURITYにする =====
        if (ownerUuid != null) {
            boolean ownerOnline = (getOwnerPlayer() != null);

            // ただし寝てる/退場中はAIいじらない（状態機械の方が優先）
            StudentLifeState ls = this.lifeState; // serverなので直に見てOK
            boolean lifeLocksAi = (ls == StudentLifeState.EXITING
                    || ls == StudentLifeState.SLEEPING
                    || ls == StudentLifeState.RECOVERING);

            if (!lifeLocksAi) {
                if (!ownerOnline) {
                    // オーナーが居ない → SECURITY強制
                    if (!forcedSecurityBecauseOwnerOffline) {
                        forcedSecurityBecauseOwnerOffline = true;
                        this.setAiMode(StudentAiMode.SECURITY);
                        // 必要なら拠点を現在地にする（好み）
                        if (this.securityPos == null) this.securityPos = this.getBlockPos();
                    }
                } else {
                    // オーナーが戻った → FOLLOWへ戻す（好みで SECURITY解除しないでもOK）
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
    // 強制起床
    private void forceWakeUp(ServerWorld sw, @Nullable BlockPos fallbackPos, boolean tryTurnOffBedAnim) {
        // ベッドBEのsleepフラグを下ろせるなら下ろす（壊れててもBEが残ってる場合がある）
        if (tryTurnOffBedAnim && respawnBedFoot != null) {
            var be = sw.getBlockEntity(respawnBedFoot);
            if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(false);
        }

        // 位置：安全地点優先 → fallback → 現在地
        BlockPos out = (respawnSafePos != null) ? respawnSafePos
                : (fallbackPos != null ? fallbackPos : this.getBlockPos());

        this.refreshPositionAndAngles(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, this.getYaw(), this.getPitch());
        this.setVelocity(0, 0, 0);
        this.getNavigation().stop();

        // ★重要：復帰フラグ解除
        this.setGhost(false);
        this.setAiDisabled(false);
        this.setNoGravity(false);
        this.setInvulnerable(false);

        // 状態を通常へ
        setLifeState(StudentLifeState.NORMAL);

        // 使い終わった参照をクリア
        respawnBedFoot = null;
        respawnSafePos = null;
    }


    private void tickLifeStateServer() {
        switch (lifeState) {

            case EXITING -> {
                if (lifeTimer > 0) {
                    lifeTimer--;

                    ServerWorld sw = (ServerWorld) this.getWorld();
                    if (respawnBedFoot == null || !isValidLinkedBed(sw, respawnBedFoot)) {
                        // EXITING中にベッドが無いなら、その場で復帰
                        forceWakeUp(sw, this.getBlockPos(), false);
                        break;
                    }

                    return;
                }

                // ★exit後にベッドへワープ（ベッドfoot基準で固定）
                if (respawnBedFoot != null) {
                    // yaw固定しなおし（途中でズレないように）
                    if (getWorld() instanceof ServerWorld sw) {
                        float bedYaw = getBedYaw(sw, respawnBedFoot);
                        this.setYaw(bedYaw);
                        this.setBodyYaw(bedYaw);
                        this.setHeadYaw(bedYaw);
                    }

                    this.refreshPositionAndAngles(
                            respawnBedFoot.getX() + 0.5,
                            respawnBedFoot.getY() + getSleepYOffset(),
                            respawnBedFoot.getZ() + 0.5,
                            this.getYaw(), this.getPitch()
                    );
                }

                // ベッドBEをsleepへ
                if (respawnBedFoot != null) {
                    var be = getWorld().getBlockEntity(respawnBedFoot);
                    if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(true);
                }

                // ★寝状態へ（ここから回復完了までずっとsleep）
                setLifeState(StudentLifeState.SLEEPING);
            }

            case SLEEPING -> {
                // ★完全固定（押し出し＆慣性対策）
                this.setVelocity(0,0,0);

                ServerWorld sw = (ServerWorld) this.getWorld();
                if (!isValidLinkedBed(sw, respawnBedFoot)) {
                    // ★ベッドが消えた/別物：HP満タン待ちせず強制起床（NORMAL）
                    forceWakeUp(sw, this.getBlockPos(), true);
                    break;
                }


                // 有効ならベッド位置固定
                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());



                // ★回復（頻度は好みで）
                // 例：10tickごとに1回復
                if (this.age % 30 == 0) {
                    this.heal(1f);
                }

                // ★満タンになったら復帰
                if (this.getHealth() >= this.getMaxHealth()) {
                    // ★通常時は安全地点へ出して起床（めり込み防止にもなる）
                    forceWakeUp(sw, this.getBlockPos(), true);
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
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LIFE_STATE, StudentLifeState.NORMAL.ordinal());
        this.dataTracker.startTracking(DODGE_TRIGGER, 0);

        this.dataTracker.startTracking(AIM_YAW, 0f);
        this.dataTracker.startTracking(AIM_PITCH, 0f);


        // AI mode
        this.dataTracker.startTracking(getAiModeTrackedData(), getDefaultAiMode());

        // 演出トリガー（共通）
        this.dataTracker.startTracking(SHOT_TRIGGER, 0);
        this.dataTracker.startTracking(RELOAD_TRIGGER, 0);
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
        StudentLifeState ls = getLifeStateClientSafe();



            if (ls == StudentLifeState.EXITING) {
                state.getController().setAnimation(EXIT);
                return PlayState.CONTINUE;
            }
            if (ls == StudentLifeState.SLEEPING) {
                state.getController().setAnimation(SLEEP);
                return PlayState.CONTINUE;
            }

        // ===== dodge (play once-ish) =====
        if (clientDodgeTicks > 0) {
            if (dodgeJustStarted) {
                state.getController().forceAnimationReset();
                dodgeJustStarted = false;
            }
            state.getController().setAnimation(DODGE);
            return PlayState.CONTINUE;
        }

        // ===== sit (vehicle) =====
        if (this.hasVehicle()) {
            state.getController().setAnimation(SIT);
            return PlayState.CONTINUE;
        }

        // ===== swim =====
        // 「水中にいて泳ぎ状態」判定（好みで isTouchingWater でもOK）
        if (this.isTouchingWater()) {
            state.getController().setAnimation(SWIM);
            return PlayState.CONTINUE;
        }

        // ===== jump (short) =====
        if (clientJumpTicks > 0) {
            state.getController().setAnimation(Jump);
            return PlayState.CONTINUE;
        }

        // ===== fall (loop while falling) =====
        // 地面にいない ＆ 下向き速度がある程度
        if (!this.isOnGround() && this.getVelocity().y < -0.08) {
            state.getController().setAnimation(FALL);
            return PlayState.CONTINUE;
        }

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

    // client-only: レンダラーが毎フレーム更新する
    @Environment(EnvType.CLIENT)
    private Vec3d clientMuzzleWorldPos = null;

    @Environment(EnvType.CLIENT)
    public void setClientMuzzleWorldPos(Vec3d pos) { this.clientMuzzleWorldPos = pos; }

    @Environment(EnvType.CLIENT)
    public Vec3d getClientMuzzleWorldPosOrApprox() {
        return (clientMuzzleWorldPos != null) ? clientMuzzleWorldPos : getMuzzlePosApprox();
    }
    public PlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        return this.getWorld().getPlayerByUuid(ownerUuid);
    }
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
    @Override
    public boolean damage(DamageSource source, float amount) {
        // まず通常処理（クライアント側で弄らない）
        if (this.getWorld().isClient) {
            return super.damage(source, amount);
        }

        float after = this.getHealth() - amount;

        // ★ロック中でも「ベッドが壊れた」なら強制解除
        if (lifeState == StudentLifeState.EXITING || lifeState == StudentLifeState.SLEEPING) {
            ServerWorld sw = (ServerWorld)this.getWorld();
            if (respawnBedFoot == null || !isValidLinkedBed(sw, respawnBedFoot)) {
                forceWakeUp(sw, this.getBlockPos(), true);
                // 解除したので、以降は通常ダメージ処理へ落とす
            } else {
                return false; // ベッドが健在なら無敵のまま
            }
        }

        // ★致死なら「死なないでベッドへ」
        if (after <= 0.5f) {
            startBedRespawn((ServerWorld) this.getWorld());
            return false; // ここでダメージを無効化
        }

        return super.damage(source, amount);
        
    }

    private void startBedRespawn(ServerWorld sw) {
        BlockPos bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());

        if (bed == null) {
            bed = findNearestBedFoot(sw, getStudentId(), this.getBlockPos(), 96);
            if (bed != null) BedLinkManager.setBedPos(ownerUuid, getStudentId(), bed);
        }
        if (bed == null) {
            StudentWorldState.get(sw).clearStudent(getStudentId());

            this.discard();
            return;
        }

        // ★退場開始（アニメトリガー）
        this.requestExit();

        this.setHealth(1f);
        this.getNavigation().stop();
        this.setVelocity(0, 0, 0);
        this.setAiDisabled(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);

        // ★ゴースト化（押されない＆衝突しない）
        this.setGhost(true);

        // 保存
        this.respawnBedFoot = bed;
        this.respawnSafePos = findSafeRespawnPosNearBed(sw, bed);

        // ★ベッドの向きに合わせてYaw固定（ベッドが見つかった時点で計算）
        if (isValidLinkedBed(sw, bed)) {
            float bedYaw = getBedYaw(sw, bed);
            this.setYaw(bedYaw);
            this.setBodyYaw(bedYaw);
            this.setHeadYaw(bedYaw);
        }


        // ★状態
        setLifeState(StudentLifeState.EXITING);

        // exit演出待ち（ここは後で40tickとかに）
        this.lifeTimer = 60;
    }



    @Nullable
    private BlockPos findSafeRespawnPosNearBed(ServerWorld world, BlockPos bedFootPos) {
        // ベッド上1マスを基準に周囲探索
        BlockPos base = bedFootPos.up();

        BlockPos.Mutable m = new BlockPos.Mutable();

        // 近い順に探す（半径2あれば十分）
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

        // 最悪ベッド真上
        return base;
    }

    private boolean isSafeStandPos(ServerWorld world, BlockPos pos) {
        BlockPos below = pos.down();

        var belowState = world.getBlockState(below);

        // 足場がある
        if (belowState.isAir()) return false;
        if (belowState.getCollisionShape(world, below).isEmpty()) return false;

        // 水・溶岩回避
        if (!world.getFluidState(below).isEmpty()) return false;

        // 2マス空き
        if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;
        if (!world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) return false;

        return true;
    }
    private boolean ghost = false;
    private void setGhost(boolean v) {
        ghost = v;
        this.noClip = v;   // ★壁・ベッドに押されない
    }
    @Override
    public boolean collidesWith(Entity other) {
        return !ghost && super.collidesWith(other);
    }
    @Override
    public boolean isPushable() {
        return !ghost; // ghost中は押されない
    }
    private void setLifeState(StudentLifeState s) {
        this.lifeState = s;
        if (!this.getWorld().isClient) {
            this.dataTracker.set(LIFE_STATE, s.ordinal());
        }
    }

    private StudentLifeState getLifeStateClientSafe() {
        if (this.getWorld().isClient) {
            return StudentLifeState.values()[this.dataTracker.get(LIFE_STATE)];
        }
        return this.lifeState;
    }
    protected float getBedYaw(ServerWorld world, BlockPos bedFoot) {
        BlockState st = world.getBlockState(bedFoot);

        // ベッドじゃない/プロパティ無いなら、今の向きを維持（クラッシュ回避）
        if (!(st.getBlock() instanceof OnlyBedBlock) || !st.contains(OnlyBedBlock.FACING)) {
            return this.getYaw();
        }

        // Vanilla Bed と同じなら BedBlock.FACING だが、あなたのOnlyBedBlockの実装に合わせて
        Direction dir = st.get(OnlyBedBlock.FACING); // ←ここが違う可能性あり
        float yaw = dir.asRotation();

        // モデルの正面が逆なら 180 足す
        // yaw += 180f;

        return yaw;
    }
    private boolean isValidLinkedBed(ServerWorld sw, @Nullable BlockPos bedFoot) {
        if (bedFoot == null) return false;

        BlockState foot = sw.getBlockState(bedFoot);
        if (!(foot.getBlock() instanceof OnlyBedBlock)) return false;
        if (foot.get(OnlyBedBlock.PART) != BedPart.FOOT) return false;
        if (foot.get(OnlyBedBlock.STUDENT) != getStudentId()) return false;

        // ★相方HEADが存在するかチェック
        Direction facing = foot.get(OnlyBedBlock.FACING);
        BlockPos headPos = bedFoot.offset(facing);
        BlockState head = sw.getBlockState(headPos);

        if (!(head.getBlock() instanceof OnlyBedBlock)) return false;
        if (head.get(OnlyBedBlock.PART) != BedPart.HEAD) return false;
        if (head.get(OnlyBedBlock.STUDENT) != getStudentId()) return false;
        if (head.get(OnlyBedBlock.FACING) != facing) return false;

        return true;
    }

    public void requestDodge() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(DODGE_TRIGGER, this.dataTracker.get(DODGE_TRIGGER) + 1);
    }
    @Override
    public void tickMovement() {
        super.tickMovement();

        if (this.getWorld().isClient) return;

        if (this.isTouchingWater() && !this.hasVehicle()) {
            Vec3d look = this.getRotationVec(1.0f);
            // 前方向へ加速（yは潰すと水平泳ぎが安定）
            Vec3d forward = new Vec3d(look.x, 0, look.z);
            if (forward.lengthSquared() > 1e-6) {
                forward = forward.normalize().multiply(0.03); // ここが速度
                this.addVelocity(forward.x, 0.0, forward.z);
            }
        }
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

    public void setAimAngles(float yaw, float pitch) {
        if (!getWorld().isClient) {
            dataTracker.set(AIM_YAW, yaw);
            dataTracker.set(AIM_PITCH, pitch);
        }
    }
    public float getAimYaw() { return dataTracker.get(AIM_YAW); }
    public float getAimPitch() { return dataTracker.get(AIM_PITCH); }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.getWorld().isClient) return;

        if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            StudentWorldState st = StudentWorldState.get(sw);

            // 自分が登録されているUUIDと一致する時だけ消す（安全）
            var cur = st.getStudentUuid(getStudentId());
            if (cur != null && cur.equals(this.getUuid())) {
                st.clearStudent(getStudentId());
            }
        }
    }


}
