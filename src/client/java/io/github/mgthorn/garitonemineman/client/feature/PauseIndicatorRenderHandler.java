package io.github.mgthorn.garitonemineman.client.feature;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Draws a small "PAUSED" indicator near the hotbar while {@link MinemanPauseController} is
 * engaged, so the safety toggle's state is always visible without opening a menu.
 */
public class PauseIndicatorRenderHandler {
    private static final String PAUSED_TEXT = "Mineman: PAUSED";

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mineman", "pause_indicator"), (graphics, tickCounter) -> {
            if (!MinemanPauseController.isPaused()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.hud.isHidden()) {
                return;
            }

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int textWidth = mc.font.width(PAUSED_TEXT);
            int x = screenWidth / 2 - textWidth / 2;
            int y = screenHeight - 40;

            graphics.text(mc.font, PAUSED_TEXT, x, y, 0xFFFF5555);
        });
    }
}
