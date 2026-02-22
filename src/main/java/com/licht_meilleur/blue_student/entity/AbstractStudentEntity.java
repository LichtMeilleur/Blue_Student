package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.inventory.StudentInventory;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.skill.SkillRegistry;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.StudentForm;
import com.licht_meilleur.blue_student.student.*;
import com.licht_meilleur.blue_student.weapon.WeaponSpec;
import com.licht_meilleur.blue_student.weapon.WeaponSpecs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
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
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
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
import software.bernie.geckolib.cache.object.GeoBone;


import java.util.EnumSet;
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


    @Nullable
    protected RawAnimation getOverrideAnimationIfAny() { return null; }


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

    private static final int SHOT_ANIM_TICKS = 4;
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

    // ===== queue fire =====
    private UUID queuedFireTargetUuid = null;     // main
    private UUID queuedFireSubTargetUuid = null;  // sub

    // 直前に消費された種別（AimGoalが参照）
    private boolean lastConsumedWasSub = false;



    private int lifeTimer;

    private BlockPos respawnBedFoot;
    private BlockPos respawnSafePos;


    // ★ shot の “0フレーム” を潰すための保持（1tickで十分）
    private int clientShotHoldTicks = 0;
    private static final int SHOT_HOLD_TICKS = 1;

    // ===== Evade state =====
    private boolean evading = false;

    // ===== No-fall grace (common) =====
    public int noFallTicks = 0;
    private boolean bs_wasOnGround = true;



    // ===== ゴースト =====
    private boolean ghost = false;

    // ★食べてる最中の表示用（Rendererで右手表示に使う）
    // -1 なら非表示
    private int eatingSlot = -1;
    private int eatingServerTicks = 0;

    private final LookRequest lookReq = new LookRequest();


    // skill state
    private int skillCooldownTicks = 0;
    private int skillActiveTicksLeft = 0;

    // client animation trigger用（DataTrackerでも可）
    private int skillTrigger = 0; // 発動のたびに+1してクライアントに知らせる

    // ===== Guard buff（ホシノ固有で使う“共通API”）=====
    protected static final UUID GUARD_ARMOR_UUID =
            UUID.fromString("b3a2fba6-5c73-4d8f-a10b-0b3f6c7f8a01");
    protected static final UUID GUARD_MAXHP_UUID =
            UUID.fromString("6b6c9c2a-43a8-4d4f-9aa2-2e23c77c1c02");

    protected boolean guardBuffApplied = false;

    // ===== キサキ =====
    protected static final UUID KISAKI_ARMOR_UUID =
            UUID.fromString("2ddc9b7c-7c72-4e86-b8e5-3f7a2df5d6b1");
    protected static final UUID KISAKI_MAXHP_UUID =
            UUID.fromString("9c3f0c47-3f2b-4dd1-9f1b-7e6b7b7fd11a");

    protected boolean kisakiBuffApplied = false;
    protected int kisakiSupportTicks = 0;


    private int dimFollowCooldown = 0;


    private boolean owRespawnQueued = false;
    private int owRespawnCooldown = 0;

    // 次元移動（FOLLOW/Callback共通）
    private boolean dimTransferQueued = false;
    private int dimTransferCooldown = 0;

    // パック方式（移動中にentity消して復元する）
    private boolean packedForDimTransfer = false;


    private boolean isBrMode = false;

    // Look policy (MOVE DIR)
    private boolean lookMoveDir = false;
    private int lookMoveDirPriority = 0;
    private int lookMoveDirTicks = 0;





    // ===== Form (NORMAL / BR) =====
    private static final TrackedData<Integer> FORM_ID =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Integer> BR_ACTION_ID =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BR_ACTION_HOLD =
            DataTracker.registerData(AbstractStudentEntity.class, TrackedDataHandlerRegistry.INTEGER);




    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
                .add(EntityAttributes.GENERIC_ARMOR, 20.0)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 8.0);
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

        // ★ドローン同期用：サーバーで shotTrigger を進める
        if (!this.getWorld().isClient) {
            bumpShotTrigger();
        }
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
    protected void requestEat() {
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
    protected double getSleepForwardOffset() { return 0.7; }
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

    // HinaEntity の fly_shot 判定など「クライアントの射撃演出中か」を見る用
    public int getClientShotTicksForAnim() {
        if (!this.getWorld().isClient) return 0;
        return this.clientShotTicks + this.clientShotHoldTicks;
    }


    // ===== tick =====
    @Override
    public void tick() {
        super.tick();





        // ===== client：トリガーで演出タイマーを回す =====
        if (this.getWorld().isClient) {

            if (kisakiSupportTicks > 0) {
                kisakiSupportTicks--;
                if (kisakiSupportTicks == 0) {
                    applyKisakiSupportBuff(false, 0, 0, 0);
                }
            }




            int shotTrig = this.dataTracker.get(SHOT_TRIGGER);
            if (shotTrig != lastShotTrigger) {
                lastShotTrigger = shotTrig;

                WeaponSpec spec2 = WeaponSpecs.forStudent(getStudentId());

                // 昔の「キャンセル連射感」に戻る
                clientShotTicks = Math.max(1, spec2.animShotHoldTicks);
                clientShotHoldTicks = 0; // 0フレーム潰しが不要なら消してもOK
                shotJustStarted = true;


                // ★0フレーム潰し（任意だが安定）
                clientShotHoldTicks = 2;

                shotJustStarted = true;
            } else {
                if (clientShotTicks > 0) {
                    clientShotTicks--;
                    if (clientShotTicks == 0) {
                        clientShotHoldTicks = 2;
                    }
                } else if (clientShotHoldTicks > 0) {
                    clientShotHoldTicks--;
                }
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


        tickBrActionTimer();


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

        // ★フォームのTick（まだ使ってないなら後でOK）
        tickFormFromEquipment();


        if (this.age % 20 == 0) {
            tickFormFromEquipment();
        }

        // 食事の表示タイマー
        if (eatingServerTicks > 0) {
            eatingServerTicks--;
            if (eatingServerTicks <= 0) eatingSlot = -1;
        }


        // ★スキル共通Tick（まだ使ってないなら後でOK）
        tickSkillCommon();



        // server tick のどこか（lifeLock解除後・ownerOnline判定の後あたりが安全）
        if (dimTransferCooldown > 0) dimTransferCooldown--;

        if (owRespawnCooldown > 0) owRespawnCooldown--;


        handleFollowDimTransfer();

        PlayerEntity owner = getOwnerPlayer();
        if (owner instanceof ServerPlayerEntity sp) {
            // ★復活中はディメンション追従しない（復活処理とぶつかる）
            if (!isLifeLockedForGoal() && getAiMode() == StudentAiMode.FOLLOW) {
                if (sp.getServerWorld() != this.getWorld()) {
                    queueTeleportToOwnerDimension(sp); // ★引数は1つ
                }
            }
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

        // ★LOOKポリシー適用（SIDE中は体Yawを移動方向へ）
        tickLookPolicies();


        // ★ 現在位置を常に保存（5tickに1回で十分軽い）
        if (this.age % 5 == 0 && this.getWorld() instanceof ServerWorld sw) {
            StudentWorldState.get(sw)
                    .updatePos(getStudentId(), sw, this.getBlockPos());
        }


    }

    private void applyStatsFromStudentId() {
        StudentId id = getStudentId();

        EntityAttributeInstance mh = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (mh != null) mh.setBaseValue(id.getBaseMaxHp());

        EntityAttributeInstance ar = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (ar != null) ar.setBaseValue(id.getBaseDefense());

        // ★ここが原因：復活中は満タンにしない
        if (!isLifeLocked()) {
            this.setHealth(this.getMaxHealth());
        } else {
            // 復活中は「上限超えだけ丸める」
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }
    }

    /**
     * ★ホシノのガードで使う（共通API）
     * - on=true で防御/最大HPを加算
     * - on=false で元に戻す
     * ここは「ホシノだけが呼ぶ」想定。
     */
    protected void applyGuardBuff(boolean on, double addArmor, double addMaxHp, float healOnApply) {
        var armor = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        var maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

        if (on) {
            if (guardBuffApplied) return;
            guardBuffApplied = true;

            if (armor != null) {
                armor.removeModifier(GUARD_ARMOR_UUID);
                armor.addPersistentModifier(new EntityAttributeModifier(
                        GUARD_ARMOR_UUID,
                        "guard_armor",
                        addArmor,
                        EntityAttributeModifier.Operation.ADDITION
                ));
            }

            if (maxHp != null) {
                maxHp.removeModifier(GUARD_MAXHP_UUID);
                maxHp.addPersistentModifier(new EntityAttributeModifier(
                        GUARD_MAXHP_UUID,
                        "guard_maxhp",
                        addMaxHp,
                        EntityAttributeModifier.Operation.ADDITION
                ));
            }

            // HP増加分だけ少し回復（最大まで全回復はしない）
            float newMax = this.getMaxHealth();
            if (healOnApply > 0 && this.getHealth() < newMax) {
                this.setHealth(Math.min(newMax, this.getHealth() + healOnApply));
            }

        } else {
            if (!guardBuffApplied) return;
            guardBuffApplied = false;

            if (armor != null) armor.removeModifier(GUARD_ARMOR_UUID);
            if (maxHp != null) maxHp.removeModifier(GUARD_MAXHP_UUID);

            // 現在HPが新max超えたら丸める
            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }
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

        // 事故フラグ解除
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
        ServerWorld sw = (ServerWorld) this.getWorld();




        if (isLifeLocked()) {
            this.setAiDisabled(true);
            this.setNoGravity(true);
            this.setGhost(true);
            this.setInvulnerable(false);
        }




        // ★復活系は Overworld でしか進めない（別次元死亡の安定化）
        if (isLifeLocked() && respawnBedFoot != null) {
            ServerWorld ow = sw.getServer().getOverworld();
            if (ow != null && sw != ow) {
                queueTeleportToOverworldForRespawn(ow);
                return;
            }
        }


        // 復活処理中にベッドが壊れたら即復帰
        boolean bedOk = (respawnBedFoot != null && isValidLinkedBed(sw, respawnBedFoot));
        if (!bedOk && isLifeLocked()) {
            StudentWorldState.get(sw.getServer()).clearBed(getStudentId());
            forceWakeUp(sw, this.getBlockPos(), true);

            return;
        }

        switch (lifeState) {
            case NORMAL -> {
                // NORMALは常に復帰保証
                this.setGhost(false);
                this.setAiDisabled(false);
                this.setNoGravity(false);
                this.setInvulnerable(false);
                return;
            }

            case EXITING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                if (lifeTimer > 0) {
                    lifeTimer--;
                    return;
                }

                setLifeState(StudentLifeState.RESPAWN_DELAY);
                lifeTimer = 10;
            }

            case RESPAWN_DELAY -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                if (lifeTimer > 0) {
                    lifeTimer--;
                    return;
                }

                setLifeState(StudentLifeState.WARPING_TO_BED);
            }

            case WARPING_TO_BED -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                var be = sw.getBlockEntity(respawnBedFoot);
                if (be instanceof OnlyBedBlockEntity obe) obe.setSleepAnim(true);

                setLifeState(StudentLifeState.SLEEPING);
                return;
            }

            case SLEEPING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                setLifeState(StudentLifeState.RECOVERING);
            }

            case RECOVERING -> {
                this.setVelocity(0, 0, 0);
                this.getNavigation().stop();

                Vec3d p = getSleepPos(sw, respawnBedFoot);
                float yaw = getBedYaw(sw, respawnBedFoot);
                this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
                this.refreshPositionAndAngles(p.x, p.y, p.z, yaw, this.getPitch());

                if (this.age % 30 == 0) {
                    this.heal(1f);
                }

                if (this.getHealth() >= this.getMaxHealth()) {
                    forceWakeUp(sw, this.getBlockPos(), true);
                }
            }

            default -> setLifeState(StudentLifeState.NORMAL);
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

    // ===== ghost =====
    private void setGhost(boolean v) {
        ghost = v;
        this.noClip = v;
    }

    @Override
    public boolean isAttackable() {
        return !isLifeLocked() && super.isAttackable();
    }

    @Override
    public boolean isPushable() {
        return !ghost && !isLifeLocked() && super.isPushable();
    }

    @Override
    public boolean collidesWith(Entity other) {
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

        // ★寝る向きだけ反転（180度）
        return dir.getOpposite().asRotation();
        // もしくは: return dir.asRotation() + 180.0f;
    }


    private boolean isValidLinkedBed(ServerWorld sw, @Nullable BlockPos bedFoot) {
        if (sw == null || bedFoot == null) return false;

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

        // 復活系は最優先
        if (ls == StudentLifeState.EXITING) {
            state.getController().setAnimation(EXIT);
            return PlayState.CONTINUE;
        }

        if (ls == StudentLifeState.WARPING_TO_BED) {
            state.getController().setAnimation(IDLE);
            return PlayState.CONTINUE;
        }

        if (ls == StudentLifeState.SLEEPING || ls == StudentLifeState.RECOVERING) {
            state.getController().setAnimation(SLEEP);
            return PlayState.CONTINUE;
        }

        RawAnimation ov = getOverrideAnimationIfAny();
        if (ov != null) {
            state.getController().setAnimation(ov);
            return PlayState.CONTINUE;
        }

        if (clientEatTicks > 0) {
            if (eatJustStarted) {
                state.getController().forceAnimationReset();
                eatJustStarted = false;
            }
            state.getController().setAnimation(ACTION);
            return PlayState.CONTINUE;
        }

        // 先に BR action を最優先にする（すでにあなたが入れてる部分を “もっと上” に置くのが重要）
        if (getForm() == StudentForm.BR) {
            var a = getBrAction();
            if (a != StudentBrAction.NONE) {
                RawAnimation brAnim = getBrAnimationForAction(a);
                if (brAnim != null) {
                    state.getController().setAnimation(brAnim);
                    return PlayState.CONTINUE;
                }
            }
        }

// BR中は旧dodgeトリガーを無視（ログも止まる）
        if (clientDodgeTicks > 0) {
            if (getForm() != StudentForm.BR) {
                if (dodgeJustStarted) {
                    state.getController().forceAnimationReset();
                    dodgeJustStarted = false;
                }
                state.getController().setAnimation(DODGE);
                return PlayState.CONTINUE;
            }
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

        if (getForm() == StudentForm.BR) {
            var a = getBrAction();
            if (a != com.licht_meilleur.blue_student.student.StudentBrAction.NONE) {
                RawAnimation brAnim = getBrAnimationForAction(a);
                if (brAnim != null) {
                    state.getController().setAnimation(brAnim);
                    return PlayState.CONTINUE;
                }
                // nullなら共通のSHOT/RELOAD等にフォールバック
            }
        }


        if (clientShotTicks > 0 || clientShotHoldTicks > 0) {
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
        Vec3d right = look.crossProduct(up).normalize(); // 右方向ベクトル

        Vec3d off;
        if (this.getForm() == StudentForm.BR && this.getStudentId() == StudentId.HOSHINO) {
            off = new Vec3d( /* BRホシノ用に調整した値 */ 0.60, -0.80, 1.00 );
        } else {
            off = StudentId.fromKey(getStudentId().asString()).getMuzzleOffset();
        }
        // ※ getStudentId() が StudentId を返すなら fromKey いらない

        return eye
                .add(look.multiply(off.x))   // 前
                .add(0, off.y, 0)            // 上下
                .add(right.multiply(off.z)); // 右
    }

    //　マズル位置
    @Environment(EnvType.CLIENT)
    private Vec3d clientMuzzleWorldPos = null;

    @Environment(EnvType.CLIENT)
    public void setClientMuzzleWorldPos(Vec3d pos) { this.clientMuzzleWorldPos = pos; }

    @Environment(EnvType.CLIENT)
    public Vec3d getClientMuzzleWorldPosOrApprox() {
        return (clientMuzzleWorldPos != null) ? clientMuzzleWorldPos : getMuzzlePosApprox();
    }

    @Environment(EnvType.CLIENT)
    private Vec3d clientSubMuzzleWorldPos = null;

    @Environment(EnvType.CLIENT)
    public void setClientSubMuzzleWorldPos(Vec3d pos) { this.clientSubMuzzleWorldPos = pos; }

    @Environment(EnvType.CLIENT)
    public Vec3d getClientSubMuzzleWorldPosOrApprox() {
        return (clientSubMuzzleWorldPos != null) ? clientSubMuzzleWorldPos : getClientMuzzleWorldPosOrApprox();
    }

    // ===== movement tweak =====
    @Override
    public void tickMovement() {
        if (!this.getWorld().isClient && isLifeLocked()) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            this.getNavigation().stop();
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;

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

            return;
        }

        super.tickMovement();

        // ★ここに追加（共通 no-fall 更新）
        if (!this.getWorld().isClient) {
            boolean onGroundNow = this.isOnGround();

            // 地上→空中に移った瞬間（崖落ち/ノックバック対策）
            if (bs_wasOnGround && !onGroundNow) {
                noFallTicks = Math.max(noFallTicks, 20); // 最低1秒
            }
            bs_wasOnGround = onGroundNow;

            // 減算
            if (noFallTicks > 0) noFallTicks--;

            // ★空中にいる限り、猶予があるなら切れないように維持
            if (!onGroundNow && noFallTicks > 0) {
                noFallTicks = Math.max(noFallTicks, 5);
            }

            // 着地したら“消す”のが好みならここで0にしてOK（安全側なら残しても良い）
            // if (onGroundNow) noFallTicks = 0;
        }

        if (this.getWorld().isClient) return;

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

        nbt.putInt("StudentForm", this.dataTracker.get(FORM_ID));

        nbt.putInt("DimTransferCooldown", dimTransferCooldown);
        nbt.putBoolean("DimTransferQueued", dimTransferQueued);

        nbt.putBoolean("ForcedSecurityOffline", forcedSecurityBecauseOwnerOffline);

        nbt.putInt("LifeState", this.lifeState.ordinal());
        nbt.putInt("LifeTimer", this.lifeTimer);

        if (this.respawnBedFoot != null) nbt.putLong("RespawnBedFoot", this.respawnBedFoot.asLong());
        if (this.respawnSafePos != null) nbt.putLong("RespawnSafePos", this.respawnSafePos.asLong());


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

        if (nbt.contains("StudentForm")) {
            this.dataTracker.set(FORM_ID, nbt.getInt("StudentForm"));
        }


        dimTransferCooldown = nbt.getInt("DimTransferCooldown");
        dimTransferQueued = nbt.getBoolean("DimTransferQueued");




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

        // 事故フラグ強制解除
        this.setInvulnerable(false);
        this.setNoGravity(false);
        this.noClip = false;
        this.setAiDisabled(false);
        this.ghost = false;


        if (nbt.contains("LifeState")) {
            int idx = nbt.getInt("LifeState");
            idx = Math.max(0, Math.min(idx, StudentLifeState.values().length - 1));
            this.lifeState = StudentLifeState.values()[idx];
            this.dataTracker.set(LIFE_STATE, this.lifeState.ordinal());
        }

        this.lifeTimer = nbt.getInt("LifeTimer");

        this.respawnBedFoot = nbt.contains("RespawnBedFoot")
                ? BlockPos.fromLong(nbt.getLong("RespawnBedFoot")) : null;

        this.respawnSafePos = nbt.contains("RespawnSafePos")
                ? BlockPos.fromLong(nbt.getLong("RespawnSafePos")) : null;


        this.eatingSlot = -1;
        this.eatingServerTicks = 0;

        // ガード解除（念のため）
        guardBuffApplied = false;
        var armor = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        var maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (armor != null) armor.removeModifier(GUARD_ARMOR_UUID);
        if (maxHp != null) maxHp.removeModifier(GUARD_MAXHP_UUID);
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

        this.dataTracker.startTracking(FORM_ID, 0);
        this.dataTracker.startTracking(BR_ACTION_ID, com.licht_meilleur.blue_student.student.StudentBrAction.NONE.ordinal());
        this.dataTracker.startTracking(BR_ACTION_HOLD, 0);


    }


    // ===== remove =====
    @Override
    public void remove(RemovalReason reason) {
        // ★ディメンション移動中は state を消さない
        if (reason == RemovalReason.CHANGED_DIMENSION) {
            super.remove(reason);
            return;
        }

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


    // exit演出用（あなたの既存実装に合わせて）
    public void requestExit() {
        // あなたの実装に合わせてください（必要ならTrackedDataを追加して++）
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

        if (isLifeLocked()) return;

        float after = this.getHealth() - amount;

        if (after <= 0.5f) {
            startBedRespawn((ServerWorld) this.getWorld());
            return;
        }

        super.applyDamage(source, amount);
    }

    // ====== ベッド復活：Overworld固定版（インベントリ保持）=====
    private void startBedRespawn(ServerWorld sw) {
        MinecraftServer server = sw.getServer();
        ServerWorld overworld = server.getOverworld();

        StudentWorldState st = StudentWorldState.get(server);

        // 1) state優先
        BlockPos bed = st.getBed(getStudentId());

        // 2) 後方互換：BedLinkManager（同一セッション内で有効）
        if (bed == null && ownerUuid != null) {
            bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());
            if (bed != null) st.setBed(getStudentId(), bed); // ★stateへ移す
        }

        // 3) 最後の保険：Overworldで近くをスキャン（昔の挙動）
        if (bed == null) {
            bed = findNearestBedFoot(overworld, getStudentId(), this.getBlockPos(), 96);
            if (bed != null) st.setBed(getStudentId(), bed); // ★stateへ移す
        }

        this.respawnBedFoot = null;
        this.respawnSafePos = null;

        if (bed == null) {
            st.clearStudent(getStudentId());
            this.discard();
            return;
        }

        if (!isValidLinkedBed(overworld, bed)) {
            st.clearBed(getStudentId());
            st.clearStudent(getStudentId());
            this.discard();
            return;
        }

        this.respawnBedFoot = bed;
        this.respawnSafePos = findSafeRespawnPosNearBed(overworld, bed);

        this.setHealth(1f);
        this.getNavigation().stop();
        this.setVelocity(0, 0, 0);

        this.setAiDisabled(true);
        this.setNoGravity(true);
        this.setGhost(true);
        this.setInvulnerable(false);

        this.requestExit();

        setLifeState(StudentLifeState.EXITING);
        this.lifeTimer = 60;

        if (this.getWorld() != overworld) {
            queueTeleportToOverworldForRespawn(overworld);
        }
        System.out.println("[BlueStudent] startBedRespawn");
        System.out.println("  CurrentDim = " + sw.getRegistryKey().getValue());
        System.out.println("  BedPos = " + bed);
        System.out.println("  LifeState = " + lifeState);


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

    // Goalから参照するだけの薄い公開API
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

    // ===== skill common（未実装のため無効化中）=====
    private void tickSkillCommon() {
        if (skillCooldownTicks > 0) skillCooldownTicks--;

        if (skillActiveTicksLeft > 0) {
            skillActiveTicksLeft--;

            // ★ スキル未実装なので何もしない
            if (skillActiveTicksLeft == 0) {
                skillCooldownTicks = 0;
            }
        }
    }


    public boolean isSkillActive() { return skillActiveTicksLeft > 0; }
    public boolean canStartSkill() { return skillCooldownTicks <= 0 && skillActiveTicksLeft <= 0; }

    public void startSkillNow() {
        if (!canStartSkill()) return;

        // ★ スキル未実装なので「時間だけ消費」
        skillActiveTicksLeft = 40; // 仮：2秒くらい（適当でOK）

        skillTrigger++; // アニメ用だけ残してOK
    }


    // ===== エイム補正（あなたの既存）=====
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
    // Goal側から呼べるように公開
    public boolean isBadFoodItemForGoal(net.minecraft.item.ItemStack st) {
        return isBadFoodItem(st); // 既存privateを使う
    }

    // もし isBadFoodItem 自体が無いなら最低限これを追加
    private boolean isBadFoodItem(net.minecraft.item.ItemStack st) {
        if (st == null || st.isEmpty()) return true;

        // 安全確定のブラックリスト（好みで追加）
        if (st.isOf(net.minecraft.item.Items.ROTTEN_FLESH)) return true;
        if (st.isOf(net.minecraft.item.Items.POISONOUS_POTATO)) return true;
        if (st.isOf(net.minecraft.item.Items.SPIDER_EYE)) return true;
        if (st.isOf(net.minecraft.item.Items.PUFFERFISH)) return true;
        if (st.isOf(net.minecraft.item.Items.CHORUS_FRUIT)) return true;
        if (st.isOf(net.minecraft.item.Items.SUSPICIOUS_STEW)) return true;

        return false;
    }

    @Override
    public boolean isEvading() {
        return evading;
    }

    @Override
    public void setEvading(boolean v) {
        this.evading = v;
    }


    //@Override
    public LookRequest getLookRequest() {
        return lookReq;
    }

    private boolean canOverrideLook(int prio) {
        // hold中で、今のprioの方が強いなら上書き拒否
        return !(lookReq.holdTicks > 0 && prio < lookReq.priority);
    }

    @Override
    public void requestLookTarget(LivingEntity t, int prio, int hold) {
        if (t == null) return;
        if (!canOverrideLook(prio)) return;
        lookReq.type = LookIntentType.TARGET;
        lookReq.target = t;
        lookReq.dir = null;
        lookReq.priority = prio;
        lookReq.holdTicks = hold;
    }

    @Override
    public void requestLookAwayFrom(LivingEntity t, int prio, int hold) {
        if (t == null) return;
        if (!canOverrideLook(prio)) return;
        lookReq.type = LookIntentType.AWAY_FROM;
        lookReq.target = t;
        lookReq.dir = null;
        lookReq.priority = prio;
        lookReq.holdTicks = hold;
    }

    @Override
    public void requestLookWorldDir(Vec3d d, int prio, int hold) {
        if (d == null || d.lengthSquared() < 1e-6) return;
        if (!canOverrideLook(prio)) return;
        lookReq.type = LookIntentType.WORLD_DIR;
        lookReq.target = null;
        lookReq.dir = d;
        lookReq.priority = prio;
        lookReq.holdTicks = hold;
    }

    @Override
    public void requestLookMoveDir(int priority, int ticks) {
        if (ticks <= 0) return;

        // 既存のLookTarget/LookAway等と同じ優先度ルールに合わせる
        if (!lookMoveDir || priority >= lookMoveDirPriority) {
            this.lookMoveDir = true;
            this.lookMoveDirPriority = priority;
            this.lookMoveDirTicks = ticks;
        }
    }

    @Override
    public void requestLookPos(Vec3d pos, int priority, int holdTicks) {
        if (pos == null) return;
        lookReq.type = LookIntentType.POS;
        lookReq.pos = pos;
        lookReq.priority = priority;
        lookReq.holdTicks = holdTicks;
    }


    @Override
    public LookRequest consumeLookRequest() {
        LookRequest copy = new LookRequest();
        copy.type = lookReq.type;
        copy.target = lookReq.target;
        copy.dir = lookReq.dir;
        copy.priority = lookReq.priority;
        copy.holdTicks = lookReq.holdTicks;

        lookReq.clear();
        return copy;
    }
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        if (noFallTicks > 0) return false;
        return super.handleFallDamage(fallDistance, damageMultiplier, source);
    }

    public void setKisakiSupportTicks(int ticks) {
        kisakiSupportTicks = Math.max(kisakiSupportTicks, ticks);
    }
    public void applyKisakiSupportBuff(boolean on, double addArmor, double addMaxHp, float healOnApply) {
        var armor = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        var maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

        if (on) {
            if (kisakiBuffApplied) return;
            kisakiBuffApplied = true;

            if (armor != null && addArmor != 0) {
                armor.removeModifier(KISAKI_ARMOR_UUID);
                armor.addPersistentModifier(new EntityAttributeModifier(
                        KISAKI_ARMOR_UUID, "kisaki_support_armor", addArmor,
                        EntityAttributeModifier.Operation.ADDITION
                ));
            }

            if (maxHp != null && addMaxHp != 0) {
                maxHp.removeModifier(KISAKI_MAXHP_UUID);
                maxHp.addPersistentModifier(new EntityAttributeModifier(
                        KISAKI_MAXHP_UUID, "kisaki_support_maxhp", addMaxHp,
                        EntityAttributeModifier.Operation.ADDITION
                ));
            }

            float newMax = this.getMaxHealth();
            if (healOnApply > 0 && this.getHealth() < newMax) {
                this.setHealth(Math.min(newMax, this.getHealth() + healOnApply));
            }

        } else {
            if (!kisakiBuffApplied) return;
            kisakiBuffApplied = false;

            if (armor != null) armor.removeModifier(KISAKI_ARMOR_UUID);
            if (maxHp != null) maxHp.removeModifier(KISAKI_MAXHP_UUID);

            if (this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
        }
    }
    public boolean hasKisakiSupportBuff() {
        return kisakiBuffApplied;
    }

    public int getShotTrigger() {
        return this.dataTracker.get(SHOT_TRIGGER);
    }
    // サーバーで撃った瞬間に呼ぶ（=トリガーを進める）
    public void bumpShotTrigger() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(SHOT_TRIGGER, this.dataTracker.get(SHOT_TRIGGER) + 1);
    }



    private void queueTeleportToOwnerDimension(ServerPlayerEntity ownerHint) {
        if (this.getWorld().isClient) return;
        if (dimTransferQueued) return;
        if (dimTransferCooldown > 0) return;
        if (ownerUuid == null) return;

        dimTransferQueued = true;
        dimTransferCooldown = 40;

        MinecraftServer server = ownerHint.getServer();

        server.execute(() -> {
            dimTransferQueued = false;

            if (this.isRemoved() || !this.isAlive()) return;
            if (!(this.getWorld() instanceof ServerWorld src)) return;

            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
            if (owner == null || !owner.isAlive()) return;

            ServerWorld dest = owner.getServerWorld();
            if (dest == src) return;

            // pre-clean（重要）
            this.stopRiding();
            this.removeAllPassengers();
            this.getNavigation().stop();
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;
            this.fallDistance = 0;

            BlockPos safe = findSafeNearPlayer(dest, owner.getBlockPos());

            AbstractStudentEntity moved = teleportTo(dest, safe, owner.getYaw());
            if (moved == null) return;

            moved.setVelocity(0, 0, 0);
            moved.getNavigation().stop();
            moved.velocityDirty = true;

            moved.dimTransferCooldown = Math.max(moved.dimTransferCooldown, 40);
            moved.fallDistance = 0;
            moved.noFallTicks = Math.max(moved.noFallTicks, 20);
        });
    }



    private void queueTeleportToOverworldForRespawn(ServerWorld overworld) {
        if (this.getWorld().isClient) return;
        if (owRespawnQueued) return;
        if (owRespawnCooldown > 0) return;

        owRespawnQueued = true;
        owRespawnCooldown = 20;

        MinecraftServer server = overworld.getServer();
        server.execute(() -> {
            owRespawnQueued = false;

            if (this.isRemoved() || !this.isAlive()) return;
            if (!(this.getWorld() instanceof ServerWorld src)) return;

            ServerWorld ow = server.getOverworld();
            if (ow == null) return;
            if (src == ow) return;

            // pre-clean
            this.stopRiding();
            this.removeAllPassengers();
            this.getNavigation().stop();
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;
            this.fallDistance = 0;

            // いったん「現在座標のまま」OWへ（次のlife処理でベッド位置に寄せ直す）
            BlockPos p = this.getBlockPos();
            TeleportTarget target = new TeleportTarget(
                    new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5),
                    Vec3d.ZERO,
                    this.getYaw(),
                    this.getPitch()
            );

            Entity teleported = FabricDimensions.teleport(this, ow, target);
            if (teleported == null) {
                System.out.println("[BlueStudent] FabricDimensions.teleport FAILED");
                return;
            }

            System.out.println("[BlueStudent] FabricDimensions.teleport OK -> " +
                    ((ServerWorld) teleported.getWorld()).getRegistryKey().getValue());

            if (teleported instanceof AbstractStudentEntity moved) {
                moved.owRespawnCooldown = Math.max(moved.owRespawnCooldown, 20);
            }
        });
    }


    private BlockPos findSafeNearPlayer(ServerWorld world, BlockPos base) {
        BlockPos.Mutable m = new BlockPos.Mutable();

        // 近い順に軽く探す（半径2・高さ±2）
        for (int r = 0; r <= 2; r++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        if (isSafeStandPos(world, m)) {
                            return m.toImmutable();
                        }
                    }
                }
            }
        }
        return base.up(); // フォールバック
    }
    private @Nullable AbstractStudentEntity teleportTo(ServerWorld dest, BlockPos safe, float yaw) {
        TeleportTarget target = new TeleportTarget(
                new Vec3d(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5),
                Vec3d.ZERO,
                yaw,
                0f
        );

        Entity movedRaw = FabricDimensions.teleport(this, dest, target);
        return (movedRaw instanceof AbstractStudentEntity moved) ? moved : null;
    }
    public boolean teleportToWorldForCallback(ServerWorld dest, BlockPos spawn, float yaw) {
        if (this.getWorld().isClient) return false;
        if (isLifeLockedForGoal()) return false;

        AbstractStudentEntity moved = teleportTo(dest, spawn, yaw);
        if (moved == null) return false;

        moved.setVelocity(0,0,0);
        moved.getNavigation().stop();
        moved.velocityDirty = true;
        moved.fallDistance = 0;
        moved.noFallTicks = Math.max(moved.noFallTicks, 20);
        return true;
    }

    public boolean isLifeLockedPublic() {
        return isLifeLocked(); // 既存privateを呼ぶだけ
    }

    private static final boolean BS_DEBUG = true;

    private void bsLog(String tag) {
        if (!BS_DEBUG) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        String dim = sw.getRegistryKey().getValue().toString();
        String ls = String.valueOf(this.lifeState);
        String bed = String.valueOf(this.respawnBedFoot);
        UUID u = this.getUuid();

        System.out.println("[BlueStudent][" + tag + "] sid=" + getStudentId().asString()
                + " uuid=" + u
                + " dim=" + dim
                + " life=" + ls
                + " bed=" + bed
                + " hp=" + this.getHealth() + "/" + this.getMaxHealth()
                + " removed=" + this.isRemoved()
                + " alive=" + this.isAlive());
    }


    public void packAndDiscardForTransfer(ServerWorld currentWorld) {
        if (this.getWorld().isClient) return;
        if (packedForDimTransfer) return;
        packedForDimTransfer = true;

        bsLog("PACK_START");

        var server = currentWorld.getServer();
        var st = StudentWorldState.get(server);

        NbtCompound full = new NbtCompound();
        this.writeNbt(full);                 // ★全部入る（推奨）
        st.setPacked(getStudentId(), full);  // ★永続に保存（後述）

        // state上の「今はpacked中」を立てる
        st.setPackedFlag(getStudentId(), true);

        this.discard();
        bsLog("PACK_DISCARD");
    }

    public static boolean spawnFromPacked(ServerWorld dest, StudentId sid, BlockPos spawnPos, float yaw) {
        var server = dest.getServer();
        var st = StudentWorldState.get(server);

        NbtCompound packed = st.getPacked(sid);
        if (packed == null) return false;

        // entity生成
        Entity raw = switch (sid) {
            case SHIROKO -> BlueStudentMod.SHIROKO.create(dest);
            case HOSHINO -> BlueStudentMod.HOSHINO.create(dest);
            case HINA    -> BlueStudentMod.HINA.create(dest);
            case ALICE   -> BlueStudentMod.ALICE.create(dest);
            case KISAKI  -> BlueStudentMod.KISAKI.create(dest);
        };

        if (!(raw instanceof AbstractStudentEntity ase)) return false;

        ase.readNbt(packed); // ★全部復元
        ase.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, yaw, 0);

        dest.spawnEntity(ase);

        // state更新（uuid/dim/pos）
        st.setStudent(sid, ase.getUuid(), dest, spawnPos);

        // packed解除
        st.setPackedFlag(sid, false);
        st.clearPacked(sid);

        ase.bsLog("UNPACK_SPAWN");
        return true;
    }

    private void handleFollowDimTransfer() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (ownerUuid == null) return;
        if (isLifeLocked()) return;
        if (getAiMode() != StudentAiMode.FOLLOW) return;

        if (dimTransferCooldown > 0) { dimTransferCooldown--; return; }
        if (dimTransferQueued) return;

        var server = sw.getServer();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner == null) return;

        ServerWorld dest = owner.getServerWorld();
        if (dest == sw) return;

        dimTransferQueued = true;
        dimTransferCooldown = 40; // 2秒

        bsLog("FOLLOW_DIM_QUEUE");

        server.execute(() -> {
            dimTransferQueued = false;
            if (this.isRemoved() || !this.isAlive()) return;

            // 1) packして消す
            packAndDiscardForTransfer(sw);

            // 2) dest側にspawn（owner付近）
            BlockPos spawn = owner.getBlockPos().up();
            spawnFromPacked(dest, getStudentId(), spawn, owner.getYaw());
        });
    }
    @Override
    public boolean canUsePortals() {
        return false; // 生徒はポータルを使わない
    }


// ===== フォーム管理 =====
private void tickFormFromEquipment() {
    if (!(this.getWorld() instanceof ServerWorld sw)) return;

    ItemStack equip = this.studentInventory.getBrEquipStack();
    StudentForm desired = StudentEquipments.isBrEquipped(getStudentId(), equip) ? StudentForm.BR : StudentForm.NORMAL;

    if (getForm() != desired) {
        setForm(desired);
        applyFormStatsAndAi(desired); // ★あなたが作る（防御/HP/AI切替）
        // 永続化したいなら StudentWorldState に form 保存も後で追加
    }
}

    private ItemStack getBrEquipStack() {
        if (!(getStudentInventory() instanceof com.licht_meilleur.blue_student.inventory.StudentInventory si)) {
            return ItemStack.EMPTY;
        }
        return si.getBrEquipStack();
    }

    private void applyFormStatsAndAi(StudentForm f) {
        this.isBrMode = (f == StudentForm.BR);

        // ここで防御力/HP/ノックバック耐性などを上げたいなら後で加える
        // まずはモデル切替＆WeaponSpec切替が動く土台を作る
    }



    public StudentForm getForm() {
        // FORM_ID を int で持ってるなら
        int v = this.dataTracker.get(FORM_ID);
        return (v == 1) ? StudentForm.BR : StudentForm.NORMAL;
    }

    public void setForm(StudentForm f) {
        this.dataTracker.set(FORM_ID, (f == StudentForm.BR) ? 1 : 0);
    }




    // ===== queue fire =====

    private boolean queuedFireIsSub = false;

    @Override
    public void queueFire(LivingEntity target) {
        if (getWorld().isClient) return;
        if (target == null) return;

        // 両立させない
        queuedFireSubTargetUuid = null;

        queuedFireTargetUuid = target.getUuid();
    }

    @Override
    public void queueFireSub(LivingEntity target) {
        if (getWorld().isClient) return;
        if (target == null) return;

        // 両立させない
        queuedFireTargetUuid = null;

        queuedFireSubTargetUuid = target.getUuid();
    }

    @Override
    public boolean hasQueuedFire() {
        return queuedFireTargetUuid != null;
    }

    @Override
    public boolean hasQueuedFireSub() {
        return queuedFireSubTargetUuid != null;
    }

    @Override
    public LivingEntity consumeQueuedFireTarget() {
        if (getWorld().isClient) return null;
        if (!(getWorld() instanceof ServerWorld sw)) return null;
        if (queuedFireTargetUuid == null) return null;

        var e = sw.getEntity(queuedFireTargetUuid);

        queuedFireTargetUuid = null;
        lastConsumedWasSub = false;

        return (e instanceof LivingEntity le) ? le : null;
    }

    @Override
    public LivingEntity consumeQueuedFireSubTarget() {
        if (getWorld().isClient) return null;
        if (!(getWorld() instanceof ServerWorld sw)) return null;
        if (queuedFireSubTargetUuid == null) return null;

        var e = sw.getEntity(queuedFireSubTargetUuid);

        queuedFireSubTargetUuid = null;
        lastConsumedWasSub = true;

        return (e instanceof LivingEntity le) ? le : null;
    }

    @Override
    public boolean consumeQueuedFireIsSub() {
        // 「直前の発射がsubか？」を返すだけ
        return lastConsumedWasSub;
    }




    private com.licht_meilleur.blue_student.student.StudentBrAction brAction = com.licht_meilleur.blue_student.student.StudentBrAction.NONE;
    private int brActionTicks = 0;

    @Override
    public void requestBrAction(com.licht_meilleur.blue_student.student.StudentBrAction action, int holdTicks) {
        if (this.getWorld().isClient) return;

        this.dataTracker.set(BR_ACTION_ID, action.ordinal());
        this.dataTracker.set(BR_ACTION_HOLD, Math.max(0, holdTicks));
    }

    // 毎tick減らす（AbstractStudentEntity.tick() か tickMovement() の最後あたりに）
    private void tickBrActionTimer() {
        if (this.getWorld().isClient) return;

        int hold = this.dataTracker.get(BR_ACTION_HOLD);
        if (hold > 0) {
            hold--;
            this.dataTracker.set(BR_ACTION_HOLD, hold);
            if (hold <= 0) {
                this.dataTracker.set(BR_ACTION_ID, com.licht_meilleur.blue_student.student.StudentBrAction.NONE.ordinal());
            }
        }
    }

    // 参照用（アニメコントローラが読める）
    public com.licht_meilleur.blue_student.student.StudentBrAction getBrAction() {
        int id = this.dataTracker.get(BR_ACTION_ID);
        var vals = com.licht_meilleur.blue_student.student.StudentBrAction.values();
        if (id < 0 || id >= vals.length) return com.licht_meilleur.blue_student.student.StudentBrAction.NONE;
        return vals[id];
    }

    // ★サブクラスが「このBRアクションの時に再生するRawAnimation」を返す
    @Nullable
    protected RawAnimation getBrAnimationForAction(com.licht_meilleur.blue_student.student.StudentBrAction a) {
        return null; // デフォルト：BRアニメ無し
    }

    public boolean shouldLockBodyYawToMoveDir() {
        return lookMoveDir && lookMoveDirTicks > 0;
    }

    private void tickLookPolicies() {
        if (lookMoveDirTicks > 0) lookMoveDirTicks--;
        if (lookMoveDirTicks <= 0) lookMoveDir = false;

        if (lookMoveDir) {
            Vec3d v = this.getVelocity();
            double vx = v.x;
            double vz = v.z;

            // ナビ移動直後で速度が小さいときは暴れやすいのでガード
            if (vx * vx + vz * vz > 1.0e-4) {
                float yaw = (float)(MathHelper.atan2(vz, vx) * (180.0 / Math.PI)) - 90.0f;

                // 体を移動方向へ（headYawも揃える）
                this.setYaw(yaw);
                this.bodyYaw = yaw;
                this.headYaw = yaw;
            }
        }
    }
    public void addAmmoInMag(int add, int magSize) {
        this.ammoInMag = Math.min(magSize, this.ammoInMag + add);
    }
    // ========================================
// 射撃時にナビを止めるかどうか
// ========================================
    public boolean shouldStopNavigationForShot(boolean fireIsSub) {

        // フォーム取得
        StudentForm form = getForm();

        // ① BRフォーム
        if (form == StudentForm.BR) {

            // BRは基本「止まらない」
            // ただしメイン射撃だけ止めたいならここで分岐可能
            // 例：
            // return !fireIsSub;

            return false;
        }

        // ② 通常フォーム
        // 通常は止める
        return true;
    }




}
