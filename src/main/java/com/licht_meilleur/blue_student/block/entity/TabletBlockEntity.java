package com.licht_meilleur.blue_student.block.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.TabletScreenHandler;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;

import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class TabletBlockEntity extends BlockEntity
        implements GeoBlockEntity, ExtendedScreenHandlerFactory {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation ROT =
            RawAnimation.begin().thenLoop("animation.model.tablet_rotation");

    public TabletBlockEntity(BlockPos pos, BlockState state) {
        super(BlueStudentMod.TABLET_BE, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(ROT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =========================
    // Screen
    // =========================

    @Override
    public Text getDisplayName() {
        return Text.literal("");
    }

    /**
     * ★ クライアントへ studentEntityId を送る
     */
    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {

        int entityId = -1;

        if (world instanceof ServerWorld sw) {
            StudentWorldState state = StudentWorldState.get(sw);

            UUID uuid = state.getStudentUuid();
            if (uuid != null) {
                Entity e = sw.getEntity(uuid);
                if (e != null) {
                    entityId = e.getId();
                }
            }
        }

        buf.writeInt(entityId);
    }

    /**
     * ★ TabletScreenHandler に entityId を渡す
     */
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {

        int entityId = -1;

        if (world instanceof ServerWorld sw) {
            StudentWorldState state = StudentWorldState.get(sw);

            UUID uuid = state.getStudentUuid();
            if (uuid != null) {
                Entity e = sw.getEntity(uuid);
                if (e != null) {
                    entityId = e.getId();
                }
            }
        }

        return new TabletScreenHandler(syncId, inv, entityId);
    }
}
