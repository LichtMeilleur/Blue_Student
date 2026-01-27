// com.licht_meilleur.blue_student.entity.ShirokoEntity
package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.StudentFollowGoal;
import com.licht_meilleur.blue_student.ai.StudentSecurityGoal;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.inventory.StudentInventory;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import com.licht_meilleur.blue_student.student.StudentLifeState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
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

public class ShirokoEntity extends AbstractStudentEntity implements GeoEntity {

    // =========
    // DataTracker（Shiroko固有：shot）
    // =========
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // =========
    // GeckoLib
    // =========
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("animation.model.run");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.model.shot");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int clientShotTicks = 0;
    private int lastShotTrigger = 0;
    private boolean shotJustStarted = false;
    private StudentLifeState lifeState = StudentLifeState.NORMAL;


    public ShirokoEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }


    @Override
    public StudentInventory getStudentInventory() { return studentInventory; }

    @Override
    public UUID getOwnerUuid() { return ownerUuid; }

    @Override
    public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; }

    @Override
    public StudentId getStudentId() {
        return StudentId.SHIROKO;
    }

    @Override
    protected EnumSet<StudentAiMode> getAllowedAiModes() {
        // 例：シロコは FOLLOW/SECURITY のみ
        return EnumSet.of(StudentAiMode.FOLLOW, StudentAiMode.SECURITY);
    }

    @Override
    protected StudentAiMode getDefaultAiMode() {
        return StudentAiMode.FOLLOW;
    }


        // =========
    // Attributes
    // =========
    public static DefaultAttributeContainer.Builder createAttributes() {
        return AbstractStudentEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0);
    }

    // =========
    // shot（デバッグ）
    // =========
    public void playShotOnce() {
        if (this.getWorld().isClient) return;
        int v = this.dataTracker.get(SHOT_TRIGGER);
        this.dataTracker.set(SHOT_TRIGGER, v + 1);
    }

    // =========
    // Right click（Shirokoの既存挙動を残しつつ、基本はStudentCard）
    // =========
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        // owner check
        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        ItemStack inHand = player.getStackInHand(hand);

        // sneak+empty = bed link
        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.SHIROKO);
            player.sendMessage(Text.translatable("msg.blue_student.link_mode", "shiroko"), false);
            return ActionResult.CONSUME;
        }

        // open handled screen
        if (player instanceof ServerPlayerEntity sp) {
            sp.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                    buf.writeInt(ShirokoEntity.this.getId()); // ★これを client create() が読む
                }

               // @Override
                public Text getDisplayName() {
                    return Text.translatable("");
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                    // ★サーバーは「本物の entity の inventory」で作る
                    return new StudentScreenHandler(syncId, inv, ShirokoEntity.this);
                }
            });
            return ActionResult.CONSUME;
        }

        return ActionResult.CONSUME;
    }

    // =========
    // Tick（クライアント shot 演出）
    // =========
    @Override
    public void tick() {
        lifeState = StudentLifeState.NORMAL;
        super.tick();

        // =========================
        // クライアント：shot演出
        // =========================
        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(SHOT_TRIGGER);

            if (trig != lastShotTrigger) {
                lastShotTrigger = trig;
                clientShotTicks = 12;
                shotJustStarted = true;
            } else if (clientShotTicks > 0) {
                clientShotTicks--;
            }

            return; // ★クライアントはここで終了（重要）
        }

        // =========================
        // サーバー：復活ステートマシン
        // =========================
        switch (lifeState) {

            case SLEEPING -> {
                recoverTick++;
                if (recoverTick > 20) {
                    lifeState = StudentLifeState.RECOVERING;
                }
            }

            case RECOVERING -> {
                if (this.age % 10 == 0) {
                    this.heal(1f);
                }

                if (this.getHealth() >= this.getMaxHealth()) {
                    lifeState = StudentLifeState.NORMAL;
                    recoverTick = 0;

                    this.setNoGravity(false);

                    BlockPos bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());
                    var be = this.getWorld().getBlockEntity(bed);
                    if (be instanceof OnlyBedBlockEntity obe) {
                        obe.setSleepAnim(false);
                    }
                }
            }
        }
    }

    // =========
    // Goals
    // =========
    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(3, new StudentSecurityGoal(this, this, new StudentSecurityGoal.ISecurityPosProvider() {
            @Override public BlockPos getSecurityPos() { return ShirokoEntity.this.getSecurityPos(); }
            @Override public void setSecurityPos(BlockPos pos) { ShirokoEntity.this.setSecurityPos(pos); }
        }, 1.0));
    }

    // =========
    // DataTracker init
    // =========
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SHOT_TRIGGER, 0);
    }

    // =========
    // GeckoLib controller
    // =========
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ShirokoEntity> state) {
        if (clientShotTicks > 0) {
            if (shotJustStarted) {
                state.getController().forceAnimationReset();
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
    public void onDeath(DamageSource source) {
        super.onDeath(source);

        if (this.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld)this.getWorld();

        BlockPos bed = BedLinkManager.getBedPos(ownerUuid, getStudentId());

        // ===== ベッド無し → 完全消滅 =====
        if (bed == null) {
            StudentWorldState.get(sw).clearStudent();
            this.discard();
            return;
        }

        // ===== ベッド有り → 復活モード =====
        this.setHealth(1f);

        this.refreshPositionAndAngles(
                bed.getX() + 0.5,
                bed.getY() + 0.2,
                bed.getZ() + 0.5,
                0, 0
        );

        this.setNoGravity(true);
        this.setAiMode(StudentAiMode.SECURITY);

        this.lifeState = StudentLifeState.SLEEPING;

        // ベッドアニメON
        var be = sw.getBlockEntity(bed);
        if (be instanceof OnlyBedBlockEntity obe) {
            obe.setSleepAnim(true);
        }
    }
}
