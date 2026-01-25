package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.network.ModPackets;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class StudentScreen extends HandledScreen<StudentScreenHandler> {

    private static final Identifier BG = BlueStudentMod.id("textures/gui/student_card.png");
    private static final Identifier ARROW = BlueStudentMod.id("textures/gui/selector_arrow.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    public StudentScreen(StudentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = BG_W;
        this.backgroundHeight = BG_H;
        this.playerInventoryTitleY = 9999; // 非表示
    }

    @Override
    protected void init() {
        super.init();
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal(""), btn -> sendMode(0))
                .dimensions(x + 120, y + 155, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal(""), btn -> sendMode(1))
                .dimensions(x + 120, y + 180, 80, 20).build());
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        // 1.20.1ならこれでOK（drawTextureの引数はこの形が一番事故りにくい）
        ctx.drawTexture(BG, x, y, 0, 0, BG_W, BG_H, BG_W, BG_H);

        int mode = (handler.entity != null) ? handler.entity.getAiMode() : 0;
        int arrowX = x + 170;
        int arrowY = (mode == 0) ? (y + 160) : (y + 185);
        ctx.drawTexture(ARROW, arrowX, arrowY, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // こっちが 1.20.1 で安定（あなたの renderBackground(...) 赤線も回避）
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);

        if (handler.entity != null) {
            String name = handler.entity.getName().getString();
            float hp = handler.entity.getHealth();
            float max = handler.entity.getMaxHealth();

            ctx.drawText(this.textRenderer, name, x + 95, y + 28, 0x1A1A1A, false);
            ctx.drawText(this.textRenderer, "HP: " + (int) hp + " / " + (int) max, x + 95, y + 42, 0x1A1A1A, false);
        }
    }

    private void sendMode(int mode) {
        if (handler.entity == null) return;

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(handler.entity.getId());
        buf.writeInt(mode);
        ClientPlayNetworking.send(ModPackets.SET_AI_MODE, buf);
    }
}