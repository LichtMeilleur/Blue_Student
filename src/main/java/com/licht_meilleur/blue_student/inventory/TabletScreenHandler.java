package com.licht_meilleur.blue_student.inventory;

import com.licht_meilleur.blue_student.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;

public class TabletScreenHandler extends ScreenHandler {

    // クライアントが entity を解決するためのID（-1なら未召喚）
    public final int studentEntityId;

    // サーバー側（openHandledScreen から呼ばれる想定）
    public TabletScreenHandler(int syncId, PlayerInventory playerInv, int studentEntityId) {
        super(ModScreenHandlers.TABLET_MENU, syncId);
        this.studentEntityId = studentEntityId;
    }

    // クライアント側（ExtendedScreenHandlerType から呼ばれる）
    public TabletScreenHandler(int syncId, PlayerInventory playerInv, PacketByteBuf buf) {
        this(syncId, playerInv, buf.readInt());
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    // ★必須：タブレットはスロットが無いので移動不可
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
