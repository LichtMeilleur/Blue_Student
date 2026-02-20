package com.licht_meilleur.blue_student.client.screen;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.inventory.StudentScreenHandler;
import com.licht_meilleur.blue_student.network.ModPackets;
import com.licht_meilleur.blue_student.student.IStudentEntity;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class StudentScreen extends HandledScreen<StudentScreenHandler> {

    private static final Identifier BG    = BlueStudentMod.id("textures/gui/student_card.png");
    private static final Identifier SLOT  = BlueStudentMod.id("textures/gui/student_slot.png");
    private static final Identifier ARROW = BlueStudentMod.id("textures/gui/selector_arrow.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int SLOT_SIZE = 18;

    // ====== レイアウト（ここだけ触ればOK）======
    // 3Dモデル表示枠（左上の縦長枠に合わせる）
    private static final int MODEL_X = 40;   // BG左上からの相対
    private static final int MODEL_Y = 40;
    private static final int MODEL_W = 30;
    private static final int MODEL_H = 30;

    // 名前/HP（BG左上からの相対）
    private static final int NAME_X = 95;
    private static final int NAME_Y = 28;
    private static final int HP_X   = 95;
    private static final int HP_Y   = 42;

    // Student 3x3 スロット（ScreenHandlerのSLOT_START_X/Yと一致させる！）
    private static final int STUDENT_SLOT_X = 48;
    private static final int STUDENT_SLOT_Y = 90;

    // Hotbar（ScreenHandlerのhotbarX/hotbarYと一致させる！）
    private static final int HOTBAR_X = 48;
    private static final int HOTBAR_Y = 256 - 24;

    // AI文字位置（BG左上からの相対）
    private static final int AI_FOLLOW_X = 150;
    private static final int AI_FOLLOW_Y = 178;
    private static final int AI_SEC_X    = 140;
    private static final int AI_SEC_Y    = 200;

    // 矢印（文字の右上に置く）
    private static final int ARROW_FOLLOW_X = 200;
    private static final int ARROW_FOLLOW_Y = 170;
    private static final int ARROW_SEC_X    = 200;
    private static final int ARROW_SEC_Y    = 185;

    private static final int SKILL_X = 48;
    private static final int SKILL_Y = 170;
    private static final int WEAPON_X = 48;
    private static final int WEAPON_Y = 200;

    // 例：装備枠の背景
    private static final int EQUIP_SLOT_X = 150;
    private static final int EQUIP_SLOT_Y = 90;

    private static final int EQUIP_BG_X = 150;
    private static final int EQUIP_BG_Y = 90;
    private static final int EQUIP_BG_SIZE = 36;

    // ====== 透明クリック領域（ボタン）======
    // Follow / Security のクリック判定だけ欲しいので透明ボタンを置く
    private ButtonWidget followBtn;
    private ButtonWidget secBtn;

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

        // 透明ボタン：表示はしないがクリック判定だけ取る
        // サイズは “文字の周り” で適当に広めに
        followBtn = new InvisibleButton(x + AI_FOLLOW_X - 10, y + AI_FOLLOW_Y - 8, 90, 22, b -> sendModeByIndex(0));
        secBtn    = new InvisibleButton(x + AI_SEC_X    - 10, y + AI_SEC_Y    - 8, 110, 22, b -> sendModeByIndex(1));

        this.addDrawableChild(followBtn);
        this.addDrawableChild(secBtn);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.drawTexture(BG, x, y, 0, 0, BG_W, BG_H, BG_W, BG_H);

        // --- スロット枠：3x3 ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = x + STUDENT_SLOT_X + col * SLOT_SIZE;
                int sy = y + STUDENT_SLOT_Y + row * SLOT_SIZE;
                ctx.drawTexture(SLOT, sx, sy, 0, 0, 18, 18, 18, 18);
            }
        }

        // --- スロット枠：Hotbar 9 ---
        for (int col = 0; col < 9; col++) {
            int sx = x + HOTBAR_X + col * 18;
            int sy = y + HOTBAR_Y;
            ctx.drawTexture(SLOT, sx, sy, 0, 0, 18, 18, 18, 18);
        }

        ctx.drawTexture(SLOT, x + EQUIP_SLOT_X, y + EQUIP_SLOT_Y, 0, 0, 18, 18, 18, 18);

        // --- AI表示（参照型） ---
        IStudentEntity se = handler.entity;

// entityがnullでも落ちないように
        StudentId sid = (se != null) ? se.getStudentId() : StudentId.SHIROKO;

// ここが参照元（AI1/AI2）
        StudentAiMode[] aiList = sid.getAllowedAis();
        StudentAiMode ai1 = (aiList.length >= 1) ? aiList[0] : StudentAiMode.FOLLOW;
        StudentAiMode ai2 = (aiList.length >= 2) ? aiList[1] : StudentAiMode.SECURITY;

// 表示（文字を大きくしたいなら scale をここで）
        drawScaledText(ctx, ai1.getText().getString(), x + AI_FOLLOW_X, y + AI_FOLLOW_Y, 0x101010, 1.6f);
        drawScaledText(ctx, ai2.getText().getString(), x + AI_SEC_X,    y + AI_SEC_Y,    0x101010, 1.6f);

// --- 矢印 ---
        StudentAiMode cur = (se != null) ? se.getAiMode() : ai1;
        boolean onFirst = (cur == ai1);

        int ax = x + (onFirst ? ARROW_FOLLOW_X : ARROW_SEC_X);
        int ay = y + (onFirst ? ARROW_FOLLOW_Y : ARROW_SEC_Y);
        ctx.drawTexture(ARROW, ax, ay, 0, 0, 16, 16, 16, 16);
        //生徒詳細

        ctx.drawText(textRenderer, sid.getOnlySkillText(), x + SKILL_X, y + SKILL_Y, 0x1A1A1A, false);
        drawScaledText(ctx, "Skill",   x + 48, y + 160, 0x101010, 1.0f);
        ctx.drawText(textRenderer, sid.getWeaponText(), x + WEAPON_X, y + WEAPON_Y, 0x1A1A1A, false);
        drawScaledText(ctx, "Weapon", x + 48,    y + 190,    0x101010, 1.0f);


        // --- 装備枠（36x36） ---
        IStudentEntity se2 = handler.entity;
        StudentId sid2 = (se2 != null) ? se2.getStudentId() : StudentId.SHIROKO;

        Identifier equipSlotTex = com.licht_meilleur.blue_student.student.StudentEquipments.getBrSlotTexture(sid2);

        ctx.drawTexture(
                equipSlotTex,
                x + EQUIP_BG_X, y + EQUIP_BG_Y,
                0, 0,
                EQUIP_BG_SIZE, EQUIP_BG_SIZE,
                EQUIP_BG_SIZE, EQUIP_BG_SIZE
        );
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);

        // handler.entity はクライアントで null になる可能性があるなら resolve を使う
        IStudentEntity se = handler.entity;
        Entity e = (se instanceof Entity ent) ? ent : null;

        // ---- 名前/HP ----
        if (se != null) {
            Text nameText = Text.translatable("student.blue_student." + se.getStudentId().asString());
            ctx.drawText(this.textRenderer, nameText, x + NAME_X, y + NAME_Y, 0x1A1A1A, false);

            if (e instanceof LivingEntity le) {
                float hp  = le.getHealth();
                float max = le.getMaxHealth();
                ctx.drawText(this.textRenderer,
                        "HP: " + (int) hp + " / " + (int) max,
                        x + HP_X, y + HP_Y, 0x1A1A1A, false);
            }
        }

        // ---- 3Dモデル描画（左上の縦長枠） ----
        if (e instanceof LivingEntity le) {
            int drawX = x + MODEL_X + (MODEL_W / 2);
            int drawY = y + MODEL_Y + MODEL_H;      // 足元基準なので下端へ
            int scale = Math.min(MODEL_W, MODEL_H); // とりあえず枠基準（好みで調整）

            float rotX = (float) (drawX - mouseX);
            float rotY = (float) ((y + MODEL_Y + (MODEL_H / 2)) - mouseY);

            InventoryScreen.drawEntity(ctx, drawX, drawY, scale, rotX, rotY, le);
        }
    }

    private void sendMode(int modeId) {
        if (handler.entity == null) return;

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        // entityId
        if (handler.entity instanceof Entity e) buf.writeInt(e.getId());
        else return;

        // modeId
        buf.writeInt(modeId);
        ClientPlayNetworking.send(ModPackets.SET_AI_MODE, buf);
    }

    private void sendModeByIndex(int index) {
        if (handler.entity == null) return;

        StudentId sid = handler.entity.getStudentId();
        StudentAiMode[] aiList = sid.getAllowedAis();
        if (index < 0 || index >= aiList.length) return;

        StudentAiMode mode = aiList[index];
        sendMode(mode.id); // ←ここ重要：固定0/1じゃなく mode.id
    }

    /**
     * ★名前をlang参照で出す（後述）
     */
    private Text getStudentDisplayName(IStudentEntity se) {
        StudentId sid = se.getStudentId();
        // 例: student.blue_student.shiroko
        return Text.translatable("student.blue_student." + sid.asString());
    }

    /**
     * 透明ボタン（クリック領域だけ）
     */
    private static class InvisibleButton extends ButtonWidget {
        public InvisibleButton(int x, int y, int w, int h, PressAction onPress) {
            super(x, y, w, h, Text.literal(""), onPress, DEFAULT_NARRATION_SUPPLIER);
        }
        @Override
        public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // 何も描かない
        }
    }
    private void drawScaledText(DrawContext ctx, String text, int x, int y, int color, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawText(this.textRenderer, text, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // まず通常処理（スロット等）
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // equip枠(36x36)の当たり判定
        int bx = this.x + EQUIP_BG_X;
        int by = this.y + EQUIP_BG_Y;

        if (mouseX >= bx && mouseX < bx + EQUIP_BG_SIZE && mouseY >= by && mouseY < by + EQUIP_BG_SIZE) {

            // ★装備スロットが存在する場合だけ（supportsBr()で出してないなら null）
            int equipSlotIndex = handler.getEquipSlotIndex(); // ←下で追加する getter
            if (equipSlotIndex >= 0 && equipSlotIndex < handler.slots.size()) {
                var slot = handler.slots.get(equipSlotIndex);
                if (slot != null) {
                    // left/right クリック両対応（そのまま渡す）
                    this.onMouseClick(slot, slot.id, button, SlotActionType.PICKUP);
                    return true;
                }
            }
        }
        return false;
    }
}
