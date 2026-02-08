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

public class KisakiEntity extends AbstractStudentEntity {

    private static final TrackedData<StudentAiMode> AI_MODE =
            DataTracker.registerData(KisakiEntity.class, StudentAiMode.TRACKED);

    public static final String ANIM_BUFF = "animation.model.buff";

    private static final RawAnimation BUFF   = RawAnimation.begin().thenLoop(ANIM_BUFF);


    private static final TrackedData<Integer> BUFF_TRIGGER =
            DataTracker.registerData(KisakiEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int clientBuffTicks = 0;
    private int lastBuffTrigger = 0;
    private static final int BUFF_ANIM_TICKS = 20;

    //クールタイム//
    private int cooldownTicks = 0;
    private static final int COOLDOWN = 200; // 10秒



    private int buffCastingTicks = 0;

    public KisakiEntity(EntityType<? extends AbstractStudentEntity> type, World world) {
        super(type, world);
    }

    @Override
    public StudentId getStudentId() {
        return StudentId.KISAKI;
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
            BedLinkManager.setLinking(player.getUuid(), StudentId.KISAKI);
            player.sendMessage(net.minecraft.text.Text.translatable("msg.blue_student.link_mode", "kisaki"), false);
            return ActionResult.CONSUME;
        }

        return super.interactMob(player, hand);
    }

    protected void initGoals() {
        this.goalSelector.add(0, new StudentRideWithOwnerGoal(this, this));
        this.goalSelector.add(1, new SwimGoal(this));


        // ★バフ（MOVE/LOOK非競合なので安全）
        this.goalSelector.add(2, new KisakiBuffGoal(this, this));
        // ★Aim（向き＋射撃）: LOOK担当
        this.goalSelector.add(3, new StudentAimGoal(this, this));

        // 詰まり脱出（MOVE）
        this.goalSelector.add(4, new StudentStuckEscapeGoal(this, this));

        // 回避（MOVE） ※Combatより上
        this.goalSelector.add(5, new StudentEvadeGoal(this, this));

        // 危険回避（バニラ） ※必要ならここ（ただし強すぎるなら外す）
        this.goalSelector.add(6, new EscapeDangerGoal(this, 1.25));

        this.goalSelector.add(7, new StudentReturnToOwnerGoal(this, this, 1.35, 28.0, 2.5, 48.0, 20));

        // 角詰まり用（強いので優先度低め推奨）
        this.goalSelector.add(8, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, HostileEntity.class, 8.0f, 1.0, 1.35));

        // 戦闘（MOVE + 射撃キュー）
        this.goalSelector.add(9, new StudentCombatGoal(this, this));

        this.goalSelector.add(10, new StudentFollowGoal(this, this, 1.1));
        this.goalSelector.add(11, new StudentSecurityGoal(this, this,
                new StudentSecurityGoal.ISecurityPosProvider() {
                    @Override public BlockPos getSecurityPos() { return KisakiEntity.this.getSecurityPos(); }
                    @Override public void setSecurityPos(BlockPos pos) { KisakiEntity.this.setSecurityPos(pos); }
                },
                1.0));
        this.goalSelector.add(12, new StudentEatGoal(this, this));
    }

    // ★注意：initDataTracker() は override しない（Duplicate防止）
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BUFF_TRIGGER, 0);
    }
    public void requestBuff() {
        if (this.getWorld().isClient) return;

        this.dataTracker.set(BUFF_TRIGGER, this.dataTracker.get(BUFF_TRIGGER) + 1);

        startBuffCasting(20); // 詠唱停止
        cooldownTicks = COOLDOWN; // ← ★ここに移動（サーバー側で管理）
    }


    @Override
    public void tick() {
        super.tick();

        // ===== サーバー側 =====
        if (!this.getWorld().isClient) {

            if (cooldownTicks > 0) cooldownTicks--;

            if (buffCastingTicks > 0) {
                buffCastingTicks--;

                this.getNavigation().stop();
                this.setVelocity(0, 0, 0);
            }
        }

        // ===== クライアント側（アニメのみ） =====
        if (this.getWorld().isClient) {
            int trig = this.dataTracker.get(BUFF_TRIGGER);

            if (trig != lastBuffTrigger) {
                lastBuffTrigger = trig;
                clientBuffTicks = BUFF_ANIM_TICKS;
            }
            else if (clientBuffTicks > 0) {
                clientBuffTicks--;
            }
        }
    }

    @Override
    protected RawAnimation getOverrideAnimationIfAny() {
        if (this.getWorld().isClient && clientBuffTicks > 0) return BUFF;
        return null;
    }

    public boolean isBuffCasting() {
        return buffCastingTicks > 0;
    }

    public void startBuffCasting(int ticks) {
        this.buffCastingTicks = ticks;
    }




}
