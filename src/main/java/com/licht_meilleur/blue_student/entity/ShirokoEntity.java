package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;

public class ShirokoEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(ShirokoEntity.class, StudentAiMode.TRACKED);


    public static final String ANIM_DRONE_START = "animation.model.drone_start";
    private static final RawAnimation DRONE_START = RawAnimation.begin().thenPlay(ANIM_DRONE_START);

    private static final TrackedData<Integer> DRONE_START_TRIGGER =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SHOT_TRIGGER =
            DataTracker.registerData(ShirokoEntity.class, TrackedDataHandlerRegistry.INTEGER);


    private int clientDroneStartTicks = 0;
    private int lastDroneStartTrigger = 0;
    private static final int DRONE_START_ANIM_TICKS = 20; // ここはアニメ尺に合わせて


    public ShirokoEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    public StudentId getStudentId() {
        return StudentId.SHIROKO;
    }

    @Override
    protected TrackedData<StudentAiMode> getAiModeTrackedData() {
        return AI_MODE;
    }

    // 固有：スニーク素手でベッドリンク、他は共通カードUI
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        ItemStack inHand = player.getStackInHand(hand);

        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.SHIROKO);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "shiroko"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        // ★ドローン（非競合：MOVE/LOOKを奪わない）
        this.goalSelector.add(2, new ShirokoDroneGoal(this));

        // 回避（MOVE） ※Combatより上
        this.goalSelector.add(3, new StudentEvadeGoal(this, this));

        // 詰まり脱出（MOVE）
        this.goalSelector.add(4, new StudentStuckEscapeGoal(this, this));


        // 戦闘（MOVE + 射撃キュー）
        this.goalSelector.add(5, new StudentCombatGoal(this, this));
        // ★Aim（LOOK）: 向き＋射撃キュー
        this.goalSelector.add(6, new StudentAimGoal(this, this));

        // 危険回避（バニラ） ※必要なら
        this.goalSelector.add(7, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(8, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));

        // 角詰まり用（強いので基本OFFのままでOK）
        // this.goalSelector.add(8, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, HostileEntity.class, 8.0f, 1.0, 1.35));



        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return ShirokoEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { ShirokoEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }


    // ★注意：initDataTracker() は override しない（Duplicate防止）
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(DRONE_START_TRIGGER, 0);
        this.dataTracker.startTracking(SHOT_TRIGGER, 0);

    }
    public void requestDroneStart() {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(DRONE_START_TRIGGER, this.dataTracker.get(DRONE_START_TRIGGER) + 1);
    }
    @Override
    public void requestShot() {
        super.requestShot();
        this.dataTracker.set(SHOT_TRIGGER, this.dataTracker.get(SHOT_TRIGGER) + 1);
    }
    public int getShotTrigger() {
        return this.dataTracker.get(SHOT_TRIGGER);
    }




    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(DRONE_START_TRIGGER);
            if (trig != lastDroneStartTrigger) {
                lastDroneStartTrigger = trig;
                clientDroneStartTicks = DRONE_START_ANIM_TICKS;
            } else if (clientDroneStartTicks > 0) {
                clientDroneStartTicks--;
            }
        }
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        // drone_start を最優先で見せたいなら先に返す
        if (this.getWorld().isClient && clientDroneStartTicks > 0) {
            return DRONE_START;
        }
        return null;
    }


}
