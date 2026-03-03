package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.ai.only.HikariGunTrainGoal;
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

public class HikariEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(HikariEntity.class, StudentAiMode.TRACKED);

    // ★あなたの animation.json のキーに合わせて変更してください
    // 例: "animation.hikari.gun_train" / "animation.model.gun_train" など
    public static final String ANIM_GUN_TRAIN = "animation.model.gun_train";
    private static final RawAnimation GUN_TRAIN_LOOP =
            RawAnimation.begin().thenLoop(ANIM_GUN_TRAIN);

    // 追加：スキル中フラグ（サーバーがON/OFFしてクライアント同期）
    private static final TrackedData<Boolean> GUN_TRAIN_ACTIVE =
            DataTracker.registerData(HikariEntity.class, TrackedDataHandlerRegistry.BOOLEAN);


    public HikariEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(GUN_TRAIN_ACTIVE, false);
    }


    public void setGunTrainSkillActive(boolean active) {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(GUN_TRAIN_ACTIVE, active);
    }

    public boolean isGunTrainSkillActive() {
        return this.dataTracker.get(GUN_TRAIN_ACTIVE);
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (isGunTrainSkillActive()) return GUN_TRAIN_LOOP;
        return null;
    }

    @Override
    public StudentId getStudentId() {
        return StudentId.HIKARI; // ★ここ重要（MARIEのままになってた）
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
            BedLinkManager.setLinking(player.getUuid(), StudentId.HIKARI);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "hikari"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));

        this.goalSelector.add(2, new HikariGunTrainGoal(this));

        // ★Aim（向き＋射撃）: LOOK担当
        this.goalSelector.add(3, new StudentAimGoal(this, this));

        // 詰まり脱出（MOVE）
        this.goalSelector.add(4, new StudentStuckEscapeGoal(this, this));

        // 回避（MOVE） ※Combatより上
        this.goalSelector.add(5, new StudentEvadeGoal(this, this));

        // 危険回避（バニラ）
        this.goalSelector.add(6, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(7, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));

        // 角詰まり用
        this.goalSelector.add(8, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, HostileEntity.class, 8.0f, 1.0, 1.35));

        // 戦闘（MOVE + 射撃キュー）
        this.goalSelector.add(9, new StudentCombatGoal(this, this));

        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return HikariEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { HikariEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

    

}