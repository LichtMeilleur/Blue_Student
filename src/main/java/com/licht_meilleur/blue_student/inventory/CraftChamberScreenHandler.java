package com.licht_meilleur.blue_student.inventory;

import com.licht_meilleur.blue_student.block.entity.CraftChamberBlockEntity;
import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class CraftChamberScreenHandler extends ScreenHandler {

    public final BlockPos pos;
    public final CraftChamberBlockEntity be; // client側はnullでもOK

    public CraftChamberScreenHandler(int syncId, PlayerInventory inv, CraftChamberBlockEntity be, BlockPos pos) {
        super(ModScreenHandlers.CRAFT_CHAMBER_MENU, syncId);
        this.be = be;
        this.pos = pos;
    }
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}