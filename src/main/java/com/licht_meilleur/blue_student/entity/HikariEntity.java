package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
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
    private static final RawAnimation GUN_TRAIN = RawAnimation.begin().thenLoop(ANIM_GUN_TRAIN);

    // ★もし「一時的にこのアニメを強制再生」したいならトリガーを残すが、
    // 今回は “バフ関係削除” なので、上書きトリガー系は全部無しにする。

    public HikariEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
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

        // ★バフGoal削除

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

    /**
     * ここは「強制上書きアニメ」が不要なら null 固定でOK。
     * （idle/run/shot などは AbstractStudentEntity 側の通常コントローラで流れる想定）
     *
     * もし「常時gun_trainを優先したい」なら、条件を付けて返す。
     */
    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        // 例：常時ループさせたい場合
        // return GUN_TRAIN;

        return null;
    }
}