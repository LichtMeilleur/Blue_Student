package com.licht_meilleur.blue_student.block.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class OnlyBedBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // animation key（only_for_bed.animation.json）
    private static final RawAnimation NORMAL = RawAnimation.begin().thenLoop("animation.model.normal");
    private static final RawAnimation SLEEP  = RawAnimation.begin().thenLoop("animation.model.sleep");

    // ★サーバーがtrueにすると、クライアントでsleepアニメ再生
    private boolean sleepAnim = false;

    public OnlyBedBlockEntity(BlockPos pos, BlockState state) {
        super(BlueStudentMod.ONLY_BED_BE, pos, state);
    }

    public boolean isSleepAnim() {
        return sleepAnim;
    }

    public void setSleepAnim(boolean value) {
        if (this.sleepAnim == value) return;
        this.sleepAnim = value;
        markDirty();
        sync();
    }

    private void sync() {
        if (world == null) return;
        BlockState state = getCachedState();
        // ★BE同期：updateListeners + packetで確実に飛ばす
        world.updateListeners(pos, state, state, 3);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<OnlyBedBlockEntity> state) {
        return state.setAndContinue(this.sleepAnim ? SLEEP : NORMAL);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- NBT（保存用）
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("SleepAnim", sleepAnim);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        sleepAnim = nbt.getBoolean("SleepAnim");
    }

    // ---- クライアント同期用（★ここが重要）
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    @Nullable
    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
