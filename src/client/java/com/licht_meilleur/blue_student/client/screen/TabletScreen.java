package com.licht_meilleur.blue_student.client.screen;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TabletScreen extends Screen {

    private final BlockPos tabletPos;

    private static final Identifier BG = BlueStudentMod.id("textures/gui/tablet_screen.png");
    private static final Identifier ARROW = BlueStudentMod.id("textures/gui/selector_arrow.png");

    // 空枠用のダミー（1枚用意しておくと雰囲気出る）
    private static final Identifier EMPTY_FACE = BlueStudentMod.id("textures/gui/empty_face.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;

    private int x0, y0;

    // 10枠（2x5想定）— 実生徒5人だけ入れて、残りnull
    private final @Nullable StudentId[] slots = new StudentId[10];

    private int selectedIndex = 0; // 0..9

    public TabletScreen(BlockPos tabletPos) {
        super(Text.empty()); // ← タイトル不要なら empty
        this.tabletPos = tabletPos;

        // 実生徒（5人）
        slots[0] = StudentId.SHIROKO;
        slots[1] = StudentId.HOSHINO;
        slots[2] = StudentId.HINA;
        slots[3] = StudentId.KISAKI;
        slots[4] = StudentId.ALICE;

        // slots[5..9] は null = 空
        selectedIndex = 0;
    }

    public static void open(BlockPos tabletPos) {
        MinecraftClient.getInstance().setScreen(new TabletScreen(tabletPos));
    }

    @Override
    protected void init() {
        super.init();
        this.x0 = (this.width - BG_W) / 2;
        this.y0 = (this.height - BG_H) / 2;

        // 顔ボタンを 2x5 で配置（調整OK）
        int startX = x0 + 30;
        int startY = y0 + 40;
        int cell = 36; // 間隔
        int i = 0;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) {
                int bx = startX + col * cell;
                int by = startY + row * cell;
                final int index = i++;
                addDrawableChild(new FaceButton(bx, by, index));
            }
        }

        // プロフィールへ（選択枠が空なら何もしない）
        addDrawableChild(ButtonWidget.builder(Text.literal("Profile"), b -> {
            StudentId id = slots[selectedIndex];
            if (id == null) return;
            this.client.setScreen(new TabletStudentScreen(tabletPos, id));
        }).dimensions(x0 + 10, y0 + 220, 70, 20).build());

        // CALL（選択枠が空なら何もしない）
        addDrawableChild(ButtonWidget.builder(Text.literal("CALL"), b -> {
            StudentId id = slots[selectedIndex];
            if (id == null) return;
            sendCall(id);
            this.close();
        }).dimensions(x0 + 170, y0 + 220, 60, 20).build());

        // Back（閉じる）
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.close())
                .dimensions(x0 + 235 - 60, y0 + 220, 60, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        ctx.drawTexture(BG, x0, y0, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // 選択矢印（選択中の枠の上）
        int startX = x0 + 30;
        int startY = y0 + 40;
        int cell = 36;
        int row = selectedIndex / 5;
        int col = selectedIndex % 5;

        int arrowX = startX + col * cell + 8;
        int arrowY = startY + row * cell - 14;
        ctx.drawTexture(ARROW, arrowX, arrowY, 0, 0, 16, 16, 16, 16);

        super.render(ctx, mouseX, mouseY, delta);

        // 選択中の名前（任意）
        StudentId sel = slots[selectedIndex];
        Text t = (sel != null) ? sel.getNameText() : Text.literal("(empty)");
        ctx.drawText(textRenderer, t, x0 + 12, y0 + 12, 0x202020, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendCall(StudentId id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id.asString());
        buf.writeBlockPos(tabletPos); // ★重なり防止・召喚位置に使える
        ClientPlayNetworking.send(ModPackets.CALL_STUDENT, buf);
    }

    // =========================
    // 顔ボタン（10枠対応・空は無反応）
    // =========================
    private class FaceButton extends PressableWidget {
        private final int index;

        public FaceButton(int x, int y, int index) {
            super(x, y, 32, 32, Text.empty());
            this.index = index;
        }

        @Override
        public void onPress() {
            selectedIndex = index;
        }

        //@Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            StudentId id = slots[index];
            Identifier tex = (id != null) ? id.getFaceTexture() : EMPTY_FACE;
            ctx.drawTexture(tex, this.getX(), this.getY(), 0, 0, 32, 32, 32, 32);

            if (this.isHovered()) {
                ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, 0x80FFFFFF);
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        }
    }
}
