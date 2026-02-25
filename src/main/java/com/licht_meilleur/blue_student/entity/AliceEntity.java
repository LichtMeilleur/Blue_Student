package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.ai.br_ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentBrAction;
import com.licht_meilleur.blue_student.student.StudentForm;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;

public class AliceEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(AliceEntity.class, StudentAiMode.TRACKED);

    // normal
    public static final String ANIM_HYPER_SHOT = "animation.model.hiper_shot";

    // BR
    public static final String ANIM_HYPER_CANNON_SET = "animation.model.hyper_cannon_set";
    public static final String ANIM_HYPER_CANNON     = "animation.model.hyper_cannon";
    public static final String ANIM_LEFT_MOVE_SHOT   = "animation.model.left_move_shot";
    public static final String ANIM_LEFT_MOVE        = "animation.model.left_move";
    public static final String ANIM_RIGHT_MOVE_SHOT  = "animation.model.right_move_shot";
    public static final String ANIM_RIGHT_MOVE       = "animation.model.right_move";

    private static final RawAnimation HYPER_SHOT = RawAnimation.begin().thenPlay(ANIM_HYPER_SHOT);

    private static final RawAnimation HYPER_CANNON_SET = RawAnimation.begin().thenPlay(ANIM_HYPER_CANNON_SET);
    private static final RawAnimation HYPER_CANNON     = RawAnimation.begin().thenLoop(ANIM_HYPER_CANNON);

    private static final RawAnimation LEFT_MOVE_SHOT  = RawAnimation.begin().thenPlay(ANIM_LEFT_MOVE_SHOT);
    private static final RawAnimation LEFT_MOVE       = RawAnimation.begin().thenLoop(ANIM_LEFT_MOVE);
    private static final RawAnimation RIGHT_MOVE_SHOT = RawAnimation.begin().thenPlay(ANIM_RIGHT_MOVE_SHOT);
    private static final RawAnimation RIGHT_MOVE      = RawAnimation.begin().thenLoop(ANIM_RIGHT_MOVE);

    private static final TrackedData<Integer> HYPER_TRIGGER =
            DataTracker.registerData(AliceEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int clientHyperTicks = 0;
    private int lastHyperTrigger = 0;
    private static final int HYPER_ANIM_TICKS = 20;

    private StudentForm lastForm = null;

    public AliceEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    public StudentId getStudentId() {
        return StudentId.ALICE;
    }

    @Override
    protected TrackedData<StudentAiMode> getAiModeTrackedData() {
        return AI_MODE;
    }

    // ===== muzzle（左右） =====
    // NOTE: サーバーでは骨位置が取れないので、yawから左右オフセットする近似で安定させる
    // 後で「クライアント描画だけ骨基準」にすると最終的に綺麗になる
    public Vec3d getMuzzleLeft() {
        return getMuzzleSide(-1);
    }

    public Vec3d getMuzzleRight() {
        return getMuzzleSide(+1);
    }

    private Vec3d getMuzzleSide(int sideSign) {
        Vec3d base = this.getEyePos().subtract(0, 0.10, 0);

        float yawRad = (this.getYaw() + 90.0f) * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(MathHelper.cos(yawRad), 0, MathHelper.sin(yawRad));

        // 幅・前方オフセット（好みで調整）
        double side = 0.22 * sideSign;
        double forward = 0.12;

        Vec3d forwardVec = this.getRotationVec(1.0f).normalize().multiply(forward);
        return base.add(right.multiply(side)).add(forwardVec);
    }

    // 固有：スニーク素手でベッドリンク、他は共通カードUI
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        ItemStack inHand = player.getStackInHand(hand);

        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.ALICE);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "alice"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        this.goalSelector.add(2, new StudentAimGoal(this, this));
        this.goalSelector.add(3, new StudentStuckEscapeGoal(this, this));
        this.goalSelector.add(4, new StudentEvadeGoal(this, this));
        this.goalSelector.add(5, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(6, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));
        this.goalSelector.add(7, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, HostileEntity.class, 8.0f, 1.0, 1.35));

        // BR hyper / BR combat

        this.goalSelector.add(9, new AliceHyperShotGoal(this, this));   // normal only
        this.goalSelector.add(10, new AliceBrCombatGoal(this, this));
        this.goalSelector.add(11, new StudentCombatGoal(this, this));

        this.goalSelector.add(12, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(13, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return AliceEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { AliceEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(14, new StudentEatGoal(this, this));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(HYPER_TRIGGER, 0);
    }

    public void requestHyperShot() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(HYPER_TRIGGER, this.dataTracker.get(HYPER_TRIGGER) + 1);
    }

    @Override
    public void tick() {
        super.tick();

        // フォーム切替（サーバー側の物理状態）
        if (!this.getWorld().isClient) {
            StudentForm now = getForm();
            if (lastForm != now) {
                lastForm = now;
                onFormChangedForAlice(now);
            }
        }

        // normal hyper shot（クライアント演出）
        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(HYPER_TRIGGER);
            if (trig != lastHyperTrigger) {
                lastHyperTrigger = trig;
                clientHyperTicks = HYPER_ANIM_TICKS;
            } else if (clientHyperTicks > 0) {
                clientHyperTicks--;
            }
        }
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (this.getWorld().isClient && clientHyperTicks > 0) {
            return HYPER_SHOT;
        }
        return null;
    }

    @Override
    protected RawAnimation getBrAnimationForAction(StudentBrAction a) {
        return switch (a) {
            case HYPER_CANNON_SET -> HYPER_CANNON_SET;
            case HYPER_CANNON     -> HYPER_CANNON;
            case LEFT_MOVE        -> LEFT_MOVE;
            case LEFT_MOVE_SHOT   -> LEFT_MOVE_SHOT;
            case RIGHT_MOVE       -> RIGHT_MOVE;
            case RIGHT_MOVE_SHOT  -> RIGHT_MOVE_SHOT;
            default -> null;
        };
    }

    private void onFormChangedForAlice(StudentForm now) {
        if (now == StudentForm.BR) {
            this.setNoGravity(true);
            this.fallDistance = 0;
            this.setOnGround(false);
            this.getNavigation().stop();
        } else {
            this.setNoGravity(false);
            this.noFallTicks = Math.max(this.noFallTicks, 20);
        }
    }
    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world); // 1.20.1にあります
    }
}