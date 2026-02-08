package com.licht_meilleur.blue_student.client.screen;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class TabletStudentScreen extends Screen {

    private final BlockPos tabletPos;
    private final StudentId sid;

    private static final Identifier BG   = BlueStudentMod.id("textures/gui/student_card.png");
    private static final Identifier SLOT = BlueStudentMod.id("textures/gui/student_slot.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int SLOT_SIZE = 18;

    private int x0, y0;

    // 顔枠
    private static final int FACE_X = 33;
    private static final int FACE_Y = 20;

    // 名前/HP
    private static final int NAME_X = 95;
    private static final int NAME_Y = 28;
    private static final int HP_X   = 95;
    private static final int HP_Y   = 42;

    // 生徒インベントリ枠（描画だけ）
    private static final int STUDENT_SLOT_X = 48;
    private static final int STUDENT_SLOT_Y = 90;

    // AI文字
    private static final int AI1_X = 150;
    private static final int AI1_Y = 178;
    private static final int AI2_X = 140;
    private static final int AI2_Y = 200;

    // skill/weapon
    private static final int SKILL_X = 48;
    private static final int SKILL_Y = 170;
    private static final int WEAPON_X = 48;
    private static final int WEAPON_Y = 200;

    public TabletStudentScreen(BlockPos tabletPos, StudentId sid) {
        super(Text.empty());
        this.tabletPos = tabletPos;
        this.sid = sid;
    }

    @Override
    protected void init() {
        super.init();

        this.x0 = (this.width - BG_W) / 2;
        this.y0 = (this.height - BG_H) / 2;

        // Back（右下）
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.client.setScreen(new TabletScreen(tabletPos));
        }).dimensions(x0 + 256 - 10 - 45, y0 + 228, 45, 18).build());

        // Call（左下）
        addDrawableChild(ButtonWidget.builder(Text.literal("Call"), b -> {
            sendCall();
            this.close();
        }).dimensions(x0 + 10, y0 + 228, 45, 18).build());

        // ★ CallBack（呼び戻し）…Callの右隣
        addDrawableChild(ButtonWidget.builder(Text.literal("CallBack"), b -> {
            sendCallBack();
            this.close();
        }).dimensions(x0 + 10 + 50, y0 + 228, 70, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        // BG
        ctx.drawTexture(BG, x0, y0, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // スロット枠（表示だけ）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = x0 + STUDENT_SLOT_X + col * SLOT_SIZE;
                int sy = y0 + STUDENT_SLOT_Y + row * SLOT_SIZE;
                ctx.drawTexture(SLOT, sx, sy, 0, 0, 18, 18, 18, 18);
            }
        }

        // 顔
        ctx.drawTexture(sid.getFaceTexture(), x0 + FACE_X, y0 + FACE_Y, 0, 0, 50, 50, 50, 50);

        // 名前
        ctx.drawText(textRenderer, sid.getNameText(), x0 + NAME_X, y0 + NAME_Y, 0x1A1A1A, false);

        // HP（未召喚前提）
        ctx.drawText(textRenderer,
                "HP: ? / " + sid.getBaseMaxHp() + "  DEF: " + sid.getBaseDefense(),
                x0 + HP_X, y0 + HP_Y, 0x1A1A1A, false);

        // AI（参照）
        StudentAiMode[] ai = sid.getAllowedAis();
        StudentAiMode ai1 = (ai.length >= 1) ? ai[0] : StudentAiMode.FOLLOW;
        StudentAiMode ai2 = (ai.length >= 2) ? ai[1] : StudentAiMode.SECURITY;

        drawScaledText(ctx, ai1.getText().getString(), x0 + AI1_X, y0 + AI1_Y, 0x101010, 1.6f);
        drawScaledText(ctx, ai2.getText().getString(), x0 + AI2_X, y0 + AI2_Y, 0x101010, 1.6f);

        // skill/weapon
        drawScaledText(ctx, "Skill", x0 + 48, y0 + 160, 0x101010, 1.0f);
        ctx.drawText(textRenderer, sid.getOnlySkillText(), x0 + SKILL_X, y0 + SKILL_Y, 0x1A1A1A, false);

        drawScaledText(ctx, "Weapon", x0 + 48, y0 + 190, 0x101010, 1.0f);
        ctx.drawText(textRenderer, sid.getWeaponText(), x0 + WEAPON_X, y0 + WEAPON_Y, 0x1A1A1A, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendCall() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sid.asString());
        buf.writeBlockPos(tabletPos);
        ClientPlayNetworking.send(ModPackets.CALL_STUDENT, buf);
    }

    private void drawScaledText(DrawContext ctx, String text, int x, int y, int color, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawText(this.textRenderer, text, 0, 0, color, false);
        ctx.getMatrices().pop();
    }
    private void sendCallBack() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sid.asString());
        buf.writeBlockPos(tabletPos);
        ClientPlayNetworking.send(ModPackets.CALL_BACK_STUDENT, buf);
    }
}
