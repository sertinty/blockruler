package com.sertinty.blockruler;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/**
 * 左上角的常驻面板：两点坐标、三轴格数、直线距离、体积。
 * 通过 Fabric API 的 HudElementRegistry 挂在聊天栏之后，所以不会被别的 HUD 盖住。
 */
public class RulerHud implements HudElement {

    private static final int PAD = 5;
    private static final int LINE_GAP = 2;
    private static final int BG = 0xA8101016;
    private static final int ACCENT = 0xFF3FA9F5;
    private static final int BORDER = 0x38FFFFFF;

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (!RulerState.isHudEnabled()) {
            return;
        }
        if (client.options.hudHidden) {
            return;
        }

        List<String> lines = RulerState.hudLines();
        if (lines.isEmpty()) {
            return;
        }

        var renderer = client.textRenderer;
        int lineHeight = renderer.fontHeight + LINE_GAP;

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, renderer.getWidth(line));
        }

        int width = textWidth + PAD * 2;
        int height = lines.size() * lineHeight - LINE_GAP + PAD * 2;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int x = Math.max(0, Math.min(RulerState.getHudX(), Math.max(0, screenW - width)));
        int y = Math.max(0, Math.min(RulerState.getHudY(), Math.max(0, screenH - height)));

        // 背板 + 边框 + 顶部高亮条
        context.fill(x, y, x + width, y + height, BG);
        context.fill(x, y, x + width, y + 1, ACCENT);
        context.fill(x, y + height - 1, x + width, y + height, BORDER);
        context.fill(x, y, x + 1, y + height, BORDER);
        context.fill(x + width - 1, y, x + width, y + height, BORDER);

        int textY = y + PAD;
        for (String line : lines) {
            context.drawTextWithShadow(renderer, line, x + PAD, textY, 0xFFFFFFFF);
            textY += lineHeight;
        }
    }
}
