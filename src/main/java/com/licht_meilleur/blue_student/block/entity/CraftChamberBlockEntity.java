// CraftChamberBlockEntity.java
package com.licht_meilleur.blue_student.block.entity;

import com.licht_meilleur.blue_student.BlueStudentMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CraftChamberBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CraftChamberBlockEntity(BlockPos pos, BlockState state) {
        super(BlueStudentMod.CRAFT_CHAMBER_BE, pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // craft_chamber.animation.json にある
        // "animation.model.monolith_hover" をループ
        controllers.add(new AnimationController<>(
                this, "main", 0,
                state -> {
                    state.setAnimation(RawAnimation.begin().thenLoop("animation.model.monolith_hover"));
                    return software.bernie.geckolib.core.object.PlayState.CONTINUE;
                }
        ));
    }
}