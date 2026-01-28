package com.licht_meilleur.blue_student.entity;

import com.licht_meilleur.blue_student.ai.*;
import com.licht_meilleur.blue_student.bed.BedLinkManager;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ShirokoEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(ShirokoEntity.class, StudentAiMode.TRACKED);

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
        this.goalSelector.add(1, new StudentStuckEscapeGoal(this, this));
        this.goalSelector.add(2, new EscapeDangerGoal(this, 1.25));

        // 逃げAI（安定）
        this.goalSelector.add(3, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(
                this, HostileEntity.class, 8.0f, 1.0, 1.35
        ));

        this.goalSelector.add(4, new StudentCombatGoal(this, this));
        this.goalSelector.add(5, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(6, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return ShirokoEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { ShirokoEntity.this.setSecurityPos(pos); }
                },
                1.0
        ));
    }

    // ★注意：initDataTracker() は override しない（Duplicate防止）
}
