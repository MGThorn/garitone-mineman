package io.github.mgthorn.garitonemineman.client.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.mgthorn.garitonemineman.client.feature.MinemanCancelController;

/**
 * Notices the player typing Baritone's {@code #cancel}/{@code #stop}, so our own tasks (Smart Mineman,
 * Hungry Mineman, quick deposit) stop too. Jobs themselves are read from Baritone's API, not from chat.
 */
@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void mineman$observeChatInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        String lower = message.trim().toLowerCase();

        if (lower.startsWith("#cancel") || lower.startsWith("#stop")) {
            // Baritone cancels its own job from the command, so only our tasks need stopping
            MinemanCancelController.cancelAll(false);
        }
    }
}
