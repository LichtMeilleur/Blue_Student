package com.licht_meilleur.blue_student.client.screen;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TabletScreen extends Screen {

    private final BlockPos tabletPos;

    private static final Identifier BG = BlueStudentMod.id("textures/gui/tablet_screen.png");
    private static final Identifier EMPTY_FACE = BlueStudentMod.id("textures/gui/empty_face.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private int x0, y0;

    // 10枠（2x5）: 実生徒5人＋残りnull
    private final @Nullable StudentId[] slots = new StudentId[10];

    public TabletScreen(BlockPos tabletPos) {
        super(Text.empty());
        this.tabletPos = tabletPos;

        slots[0] = StudentId.SHIROKO;
        slots[1] = StudentId.HOSHINO;
        slots[2] = StudentId.HINA;
        slots[3] = StudentId.KISAKI;
        slots[4] = StudentId.ALICE;
    }

    public static void open(BlockPos tabletPos) {
        MinecraftClient.getInstance().setScreen(new TabletScreen(tabletPos));
    }

    @Override
    protected void init() {
        super.init();
        this.x0 = (this.width - BG_W) / 2;
        this.y0 = (this.height - BG_H) / 2;

        // 顔ボタン 2x5
        int startX = x0 + 25;
        int startY = y0 + 60;
        int cell = 45;

        int i = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) {
                int bx = startX + col * cell;
                int by = startY + row * cell;
                final int index = i++;
                addDrawableChild(new FaceButton(bx, by, index));
            }
        }

        // Backだけ（重なり防止：中央寄せ）
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.close())
                .dimensions(x0 + (BG_W / 2) - 30, y0 + 220, 60, 20)
                .build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        ctx.drawTexture(BG, x0, y0, 0, 0, BG_W, BG_H, BG_W, BG_H);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // =========================
    // 顔ボタン（空は無反応）
    // =========================
    private class FaceButton extends PressableWidget {
        private final int index;

        public FaceButton(int x, int y, int index) {
            super(x, y, 32, 32, Text.empty());
            this.index = index;
        }

        @Override
        public void onPress() {
            StudentId id = slots[index];
            if (id == null) return; // 空は無反応
            client.setScreen(new TabletStudentScreen(tabletPos, id));
        }

        // ★1.20.1は renderButton をオーバーライドする
        @Override
        public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
            StudentId id = slots[index];
            Identifier tex = (id != null) ? id.getFaceTexture() : EMPTY_FACE;

            ctx.drawTexture(tex, this.getX(), this.getY(), 0, 0, 32, 32, 32, 32);

            if (this.isHovered()) {
                ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, 0x80FFFFFF);
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
    }

}