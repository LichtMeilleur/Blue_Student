package com.licht_meilleur.blue_student.ai;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.EnumSet;

public class StudentEatGoal extends Goal {

    private final PathAwareEntity mob;
    private final IStudentEntity student;

    // ===== 調整パラメータ =====
    private final float triggerHpRatio;      // 例 0.85f
    private final double hostileRadius;      // 例 10.0
    private final int eatDurationTicks;      // 例 16
    private final int eatCooldownTicks;      // 例 40
    private final int healAmount;            // 例 6（固定回復）

    // ===== 状態 =====
    private int eatTicksLeft = 0;
    private int cooldown = 0;
    private int eatingSlot = -1;

    public StudentEatGoal(PathAwareEntity mob, IStudentEntity student) {
        this(mob, student,
                0.85f,
                10.0,
                16,
                40,
                6
        );
    }

    public StudentEatGoal(PathAwareEntity mob, IStudentEntity student,
                          float triggerHpRatio,
                          double hostileRadius,
                          int eatDurationTicks,
                          int eatCooldownTicks,
                          int healAmount) {
        this.mob = mob;
        this.student = student;
        this.triggerHpRatio = triggerHpRatio;
        this.hostileRadius = hostileRadius;
        this.eatDurationTicks = eatDurationTicks;
        this.eatCooldownTicks = eatCooldownTicks;
        this.healAmount = healAmount;

        // 食べてる間は MOVE/LOOK を握って止める
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        // 復活フェーズ等なら食べない（Entity側のstateがある前提）
        if (mob instanceof AbstractStudentEntity se) {
            if (se.isLifeLockedForGoal()) return false; // ★後述の小API
        }

        StudentAiMode mode = student.getAiMode();
        if (mode != StudentAiMode.FOLLOW && mode != StudentAiMode.SECURITY) return false;

        if (cooldown > 0) return false;

        // HPが十分なら不要
        float max = mob.getMaxHealth();
        if (max <= 0.01f) return false;
        float ratio = mob.getHealth() / max;
        if (ratio >= triggerHpRatio) return false;

        // 近くに敵がいるなら食べない（戦闘/回避を優先）
        if (hasHostileNearby()) return false;

        // 食べ物がある？
        eatingSlot = findFoodSlot();
        return eatingSlot >= 0;
    }

    @Override
    public boolean shouldContinue() {
        if (eatTicksLeft <= 0) return false;
        if (!(mob.getWorld() instanceof ServerWorld)) return false;

        // 食べてる途中に敵が来たら中断
        if (hasHostileNearby()) return false;

        // スロットが空になったら中断
        if (eatingSlot < 0) return false;

        ItemStack st = getInvStack(eatingSlot);
        return !st.isEmpty() && st.isFood();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        mob.setVelocity(0, mob.getVelocity().y, 0);
        mob.velocityDirty = true;

        eatTicksLeft = eatDurationTicks;

        // 見た目用：ACTIONトリガー & 右手表示
        if (mob instanceof AbstractStudentEntity se) {
            se.requestEatFromGoal();               // ★後述の小API（requestEatを外に出す）
            se.startEatingVisualFromGoal(eatingSlot, eatDurationTicks); // ★後述
        }
    }

    @Override
    public void tick() {
        // その場で停止
        mob.getNavigation().stop();
        mob.setVelocity(0, mob.getVelocity().y, 0);
        mob.velocityDirty = true;

        // なんとなくオーナー方向を見る等したいならここで lookAt してもOK

        eatTicksLeft--;

        // 食べ終わりタイミングで効果適用（最後のtickで1回）
        if (eatTicksLeft == 0) {
            consumeAndHeal();
            cooldown = eatCooldownTicks;
            eatingSlot = -1;
        }
    }

    @Override
    public void stop() {
        eatTicksLeft = 0;
        eatingSlot = -1;
    }

    // ====== 毎tick呼ばれるから軽量に ======
    private boolean hasHostileNearby() {
        Box box = mob.getBoundingBox().expand(hostileRadius);
        // HostileEntityが1体でもいれば戦闘中扱い
        return !mob.getWorld().getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive).isEmpty();
    }

    private int findFoodSlot() {
        if (!(mob instanceof AbstractStudentEntity se)) return -1;

        for (int i = 0; i < se.getStudentInventory().size(); i++) {
            ItemStack st = se.getStudentInventory().getStack(i);
            if (st.isEmpty()) continue;
            if (!st.isFood()) continue;

            // ブラックリスト（Entity側の関数を使ってもOK）
            if (se.isBadFoodItemForGoal(st)) continue;

            FoodComponent food = st.getItem().getFoodComponent();
            if (food == null) continue;

            return i;
        }
        return -1;
    }

    private ItemStack getInvStack(int slot) {
        if (!(mob instanceof AbstractStudentEntity se)) return ItemStack.EMPTY;
        if (slot < 0 || slot >= se.getStudentInventory().size()) return ItemStack.EMPTY;
        return se.getStudentInventory().getStack(slot);
    }

    private void consumeAndHeal() {
        if (!(mob instanceof AbstractStudentEntity se)) return;

        if (eatingSlot < 0 || eatingSlot >= se.getStudentInventory().size()) return;

        ItemStack st = se.getStudentInventory().getStack(eatingSlot);
        if (st.isEmpty() || !st.isFood()) return;
        if (se.isBadFoodItemForGoal(st)) return;

        // 回復（固定）
        se.heal(healAmount);

        // 消費
        st.decrement(1);
        se.getStudentInventory().markDirty();
    }

    // ===== 外部から毎tick減らすために GoalSelector 依存でtickされる =====
    public void tickCooldown() {
        if (cooldown > 0) cooldown--;
    }
}
