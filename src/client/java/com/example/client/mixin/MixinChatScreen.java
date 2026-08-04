package com.example.client.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.feature.MinemanCancelController;

/**
 * Observes the raw text of every chat message the player submits, before anything (vanilla or any
 * other mod, including Baritone) processes it — this is the only reliable, ordering-independent way
 * to detect the player manually typing a Baritone command (e.g. "#mine diamond_ore") directly, since
 * this mod's own commands bypass the chat screen entirely (see BaritoneController.sendChat).
 */
@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void mineman$observeChatInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        String lower = message.trim().toLowerCase();

        if (lower.startsWith("#cancel") || lower.startsWith("#stop")) {
            // Player typed the cancel command themselves; it's already headed to Baritone, so don't
            // send another one — just abort every other autonomous task (SmartMineman, HungryMineman, etc).
            MinemanCancelController.cancelAll(false);
        }
        else {
            BaritoneController.observeManualChatCommand(message);
        }
    }
}
