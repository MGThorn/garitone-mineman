package com.example.client.feature;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Draws a small "PAUSED" indicator near the hotbar while {@link MinemanPauseController} is
 * engaged, so the safety toggle's state is always visible without opening a menu.
 */
public class PauseIndicatorRenderHandler {
    private static final String PAUSED_TEXT = "Mineman: PAUSED";

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            if (!MinemanPauseController.isPaused()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int textWidth = mc.font.width(PAUSED_TEXT);
            int x = screenWidth / 2 - textWidth / 2;
            int y = screenHeight - 40;

            graphics.drawString(mc.font, PAUSED_TEXT, x, y, 0xFF5555);
        });
    }
}
