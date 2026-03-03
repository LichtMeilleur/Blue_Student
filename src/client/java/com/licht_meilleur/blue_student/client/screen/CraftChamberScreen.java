package com.licht_meilleur.blue_student.client.screen;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.client.network.CraftChamberNetworking;
import com.licht_meilleur.blue_student.inventory.CraftChamberScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.licht_meilleur.blue_student.craft_chamber.CraftChamberRecipes;
import com.licht_meilleur.blue_student.craft_chamber.CraftChamberRecipe;
import com.licht_meilleur.blue_student.craft_chamber.CraftChamberRecipes;
import com.licht_meilleur.blue_student.craft_chamber.IngredientStack;
import net.minecraft.item.ItemStack;

public class CraftChamberScreen extends HandledScreen<CraftChamberScreenHandler> {

    // あなたが貼ってくれた画像をここで使う
    private static final Identifier BG = BlueStudentMod.id("textures/gui/craft_chamber.png");
    private static final Identifier BTN_L = BlueStudentMod.id("textures/gui/left_button.png");
    private static final Identifier BTN_R = BlueStudentMod.id("textures/gui/right_button.png");

    private int pageIndex = 0;


    public CraftChamberScreen(CraftChamberScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 256;  // 画像サイズに合わせて調整
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        // 左ボタン
        this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            int max = CraftChamberRecipes.ALL.size();
            pageIndex = Math.floorMod(pageIndex - 1, max);
        }).dimensions(this.x - 15, this.y + 128, 20, 20).build());

// 右ボタン
        this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            int max = CraftChamberRecipes.ALL.size();
            pageIndex = Math.floorMod(pageIndex + 1, max);
        }).dimensions(this.x + 245, this.y + 128, 20, 20).build());


        // init() の最後あたりに追加
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Craft"), b -> {
            CraftChamberNetworking.sendCraftRequest(this.handler.pos, this.pageIndex);
        }).dimensions(this.x + 12, this.y + 228, 60, 20).build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {

        // 背景
        context.drawTexture(BG, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);



        // ページ番号（仮表示）
        context.drawText(this.textRenderer, "Page " + (pageIndex + 1), this.x + 78, this.y + 10, 0xFFFFFF, false);

        // レシピ取得
        CraftChamberRecipe recipe = CraftChamberRecipes.byIndex(pageIndex);

        // ===== リング（12/3/6/9時）のアイコン =====
// 座標は背景に合わせて微調整してOK
        int cX = this.x + 128;  // 中心の基準（いま仮）
        int cY = this.y + 128;

        int slot12X = cX - 12;  // 上
        int slot12Y = cY - 93;

        int slot3X  = cX + 70; // 右
        int slot3Y  = cY - 8;

        int slot6X  = cX - 12;  // 下
        int slot6Y  = cY + 72;

        int slot9X  = cX - 95; // 左
        int slot9Y  = cY - 8;

        drawSlotIcon(context, recipe.slot12(), slot12X, slot12Y);
        drawSlotIcon(context, recipe.slot3(),  slot3X,  slot3Y);
        drawSlotIcon(context, recipe.slot6(),  slot6X,  slot6Y);
        drawSlotIcon(context, recipe.slot9(),  slot9X,  slot9Y);

// ===== 中央：完成品アイコン（拡大表示）=====
        float outScale = 4.0f; // 1.5f や 2.0f で調整
        int outX = this.x + 122;
        int outY = this.y + 125; // 見た目で微調整（中心より少し上にするなど）

        var m = context.getMatrices();
        m.push();
        m.translate(outX, outY, 0);
        m.scale(outScale, outScale, 1.0f);

// drawItemは左上基準なので、中心に置きたいなら -8,-8
        context.drawItem(recipe.output(), -8, -8);
        m.pop();

// ===== 右：素材リスト =====
        int listX = this.x + 200;
        int listY = this.y + 150;
        int stepY = 18;

        int i = 0;
        for (IngredientStack cost : recipe.costs()) {
            ItemStack s = cost.toStack();

            int ix = listX;
            int iy = listY + i * stepY;

            context.drawItem(s, ix, iy);
            //context.drawItemInSlot(this.textRenderer, s, ix, iy);

            // 個数（x5 など）
            String countText = "x" + cost.count();
            context.drawText(this.textRenderer, countText, ix + 18, iy + 5, 0xFFFFFF, true);

            i++;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        // 左右ボタン画像（ボタンWidget自体は透明で、見た目だけここで描く）
        context.drawTexture(BTN_L, this.x - 15, this.y + 128, 0, 0, 18, 18, 18, 18);
        context.drawTexture(BTN_R, this.x + 245, this.y + 128, 0, 0, 18, 18, 18, 18);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // タイトル描画を消したいなら空にしてOK
    }
    private void drawSlotIcon(DrawContext context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        context.drawItem(stack, x, y);
        context.drawItemInSlot(this.textRenderer, stack, x, y);
    }
}