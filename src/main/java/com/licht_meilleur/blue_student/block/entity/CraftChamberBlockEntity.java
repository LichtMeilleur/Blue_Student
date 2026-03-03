package com.licht_meilleur.blue_student.block.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.CraftChamberScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CraftChamberBlockEntity extends BlockEntity implements GeoBlockEntity, ExtendedScreenHandlerFactory {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CraftChamberBlockEntity(BlockPos pos, BlockState state) {
        super(BlueStudentMod.CRAFT_CHAMBER_BE, pos, state);
    }

    // ===== GeckoLib =====
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this, "main", 0,
                state -> {
                    state.setAnimation(RawAnimation.begin().thenLoop("animation.model.monolith_hover"));
                    return PlayState.CONTINUE;
                }
        ));
    }

    // ===== GUI =====

    @Override
    public Text getDisplayName() {
        return Text.literal("Craft Chamber");
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        // サーバー側は本物BEを渡す
        return new CraftChamberScreenHandler(syncId, inv, this, this.pos);
    }
}