package io.github.mgthorn.garitonemineman.client.feature;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * One status line near the hotbar while Mineman is doing something other than just running, so the
 * state is visible without opening the Task Manager. Most important first:
 * <ul>
 *   <li>"all chests full" (red): Smart Mineman is waiting for the player to make room and resume</li>
 *   <li>"PAUSED" (red): the player's pause</li>
 *   <li>"EATING" (yellow): only Hungry Mineman holds everything</li>
 *   <li>"storing items (chest 2/5, walking)" (aqua): a store trip is running</li>
 * </ul>
 * Nothing is shown while jobs simply run.
 */
public class PauseIndicatorRenderHandler {
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int AQUA = 0xFF55FFFF;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mineman", "pause_indicator"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.gui.hud.isHidden()) {
                return;
            }

            String text;
            int color;

            if (SmartMinemanHandler.isStorageFull() && MinemanPauseController.isUserPaused()) {
                text = "Mineman: all chests full, make room, then resume";
                color = RED;
            }
            else if (MinemanPauseController.isUserPaused()) {
                text = "Mineman: PAUSED";
                color = RED;
            }
            else if (MinemanPauseController.isEating()) {
                text = "Mineman: EATING";
                color = YELLOW;
            }
            else if (SmartMinemanHandler.isHandlingFullInventory()) {
                text = "Mineman: storing items" + SmartMinemanHandler.describeProgress();
                color = AQUA;
            }
            else {
                return;
            }

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int x = screenWidth / 2 - mc.font.width(text) / 2;
            int y = screenHeight - 40;

            graphics.text(mc.font, text, x, y, color);
        });
    }
}
