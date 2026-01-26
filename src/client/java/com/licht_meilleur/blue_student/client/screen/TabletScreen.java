package com.licht_meilleur.blue_student.screen;

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

public class TabletScreen extends Screen {

    // 背景（あなたの tablet_screen.png）
    private static final Identifier BG = BlueStudentMod.id("textures/gui/tablet_screen.png");
    private static final Identifier ARROW = BlueStudentMod.id("textures/gui/selector_arrow.png");

    // 背景サイズ（画像に合わせて調整してOK）
    private static final int BG_W = 256;
    private static final int BG_H = 256;

    // 顔アイコン（あなたが用意した png をそのまま使う想定）
    // assets/blue_student/textures/gui/ に置いてある前提
    private static Identifier faceTex(StudentId id) {
        return switch (id) {
            case SHIROKO -> BlueStudentMod.id("textures/gui/shiroko_face.png");
            case HOSHINO -> BlueStudentMod.id("textures/gui/hoshino_face.png");
            case HINA    -> BlueStudentMod.id("textures/gui/hina_face.png");
            case ALICE   -> BlueStudentMod.id("textures/gui/alice_face.png");
            case KISAKI  -> BlueStudentMod.id("textures/gui/kisaki_face.png");
        };
    }

    // 現在選択中
    private StudentId selected = StudentId.SHIROKO;

    // 画面左上基準（背景の描画位置）
    private int x0, y0;

    public TabletScreen() {
        super(Text.literal("Tablet"));
    }

    @Override
    protected void init() {
        super.init();

        this.x0 = (this.width - BG_W) / 2;
        this.y0 = (this.height - BG_H) / 2;

        // ---- 顔ボタン配置（位置は画像に合わせて微調整OK）
        // 例：上段に横並び5人（1人32x32）
        int startX = x0 + 40;
        int y = y0 + 45;
        int gap = 36;

        addDrawableChild(new FaceButton(startX + gap * 0, y, StudentId.SHIROKO));
        addDrawableChild(new FaceButton(startX + gap * 1, y, StudentId.HOSHINO));
        addDrawableChild(new FaceButton(startX + gap * 2, y, StudentId.HINA));
        addDrawableChild(new FaceButton(startX + gap * 3, y, StudentId.KISAKI));
        addDrawableChild(new FaceButton(startX + gap * 4, y, StudentId.ALICE));

        // ---- CALLボタン（位置は画像に合わせてOK）
        addDrawableChild(ButtonWidget.builder(Text.literal("CALL"), b -> {
            sendCall(selected);
            close();
        }).dimensions(x0 + 170, y0 + 210, 60, 20).build());

        // ESCで閉じる
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        // 背景
        ctx.drawTexture(BG, x0, y0, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // 選択矢印（顔の上に出す例）
        // 位置は FaceButton の並びと一致させる
        int startX = x0 + 40;
        int faceY = y0 + 45;
        int gap = 36;

        int idx = switch (selected) {
            case SHIROKO -> 0;
            case HOSHINO -> 1;
            case HINA    -> 2;
            case KISAKI  -> 3;
            case ALICE   -> 4;
        };

        int arrowX = startX + gap * idx + 8;
        int arrowY = faceY - 14;
        ctx.drawTexture(ARROW, arrowX, arrowY, 0, 0, 16, 16, 16, 16);

        // 通常UI描画（ボタン等）
        super.render(ctx, mouseX, mouseY, delta);

        // （任意）選択中の名前表示
        ctx.drawText(this.textRenderer,
                Text.literal("Selected: " + selected.asKey()),
                x0 + 20, y0 + 20, 0x202020, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendCall(StudentId id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id.asString()); // ★Enumは文字列で送るのが安全
        ClientPlayNetworking.send(ModPackets.CALL_STUDENT, buf);
    }

    // =========================
    // 顔ボタン
    // =========================
    private class FaceButton extends PressableWidget {
        private final StudentId id;

        public FaceButton(int x, int y, StudentId id) {
            super(x, y, 32, 32, Text.empty());
            this.id = id;
        }

        @Override
        public void onPress() {
            selected = id;
        }

        //@Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            Identifier tex = faceTex(id);
            ctx.drawTexture(tex, this.getX(), this.getY(), 0, 0, 32, 32, 32, 32);

            // hover枠（任意）
            if (this.isHovered()) {
                ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, 0x80FFFFFF);
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            // 何もしない（省略OK）
        }
    }

    // 便利：外から開く用（任意）
    public static void open() {
        MinecraftClient.getInstance().setScreen(new TabletScreen());
    }
}