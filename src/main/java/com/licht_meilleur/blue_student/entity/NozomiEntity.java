package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.ai.only.NozomiTrainGoal;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;

public class NozomiEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(NozomiEntity.class, StudentAiMode.TRACKED);

    // ★あなたの animation.json のキーに合わせて変更してください
    public static final String ANIM_TRAIN = "animation.model.train";
    private static final RawAnimation TRAIN_LOOP =
            RawAnimation.begin().thenLoop(ANIM_TRAIN);

    private static final TrackedData<Boolean> TRAIN_ACTIVE =
            DataTracker.registerData(NozomiEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public NozomiEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TRAIN_ACTIVE, false);
    }

    public void setTrainSkillActive(boolean active) {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(TRAIN_ACTIVE, active);
    }

    public boolean isTrainSkillActive() {
        return this.dataTracker.get(TRAIN_ACTIVE);
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (isTrainSkillActive()) return TRAIN_LOOP;
        return null;
    }
    @Override
    public StudentId getStudentId() {
        return StudentId.NOZOMI; // ★ここ重要（MARIEのままになってた）
    }

    @Override
    protected TrackedData<StudentAiMode> getAiModeTrackedData() {
        return AI_MODE;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        if (ownerUuid == null) setOwnerUuid(player.getUuid());
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        ItemStack inHand = player.getStackInHand(hand);

        if (player.isSneaking() && inHand.isEmpty()) {
            BedLinkManager.setLinking(player.getUuid(), StudentId.NOZOMI);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "nozomi"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        this.goalSelector.add(2, new NozomiTrainGoal(this));

        this.goalSelector.add(3, new StudentAimGoal(this, this));
        this.goalSelector.add(4, new StudentStuckEscapeGoal(this, this));
        this.goalSelector.add(5, new StudentEvadeGoal(this, this));
        this.goalSelector.add(6, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(7, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));

        this.goalSelector.add(8, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, HostileEntity.class, 8.0f, 1.0, 1.35));

        this.goalSelector.add(9, new StudentCombatGoal(this, this));

        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return NozomiEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { NozomiEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

}