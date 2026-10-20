package io.github.mgthorn.garitonemineman.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;

/**
 * Every Baritone command (#mine, #cancel, #goal, #path, #sel, #set, ...) gets echoed back
 * ("> #command") and a result printed ("Successfully set X to Y", etc.) — none of it gated by any
 * Baritone setting, confirmed by disassembling the bundled jar's command classes.
 * With {@link io.github.mgthorn.garitonemineman.client.config.Configs.Generic#HIDE_BARITONE_CHAT_FEEDBACK} enabled, this
 * drops any chat message while {@link BaritoneController#isSuppressingChatFeedback()} reports one of
 * this mod's own commands was just sent (a short window opened in {@code BaritoneController#sendChat}).
 * Since 26.3 every public entry point ({@code addClientSystemMessage}, {@code addServerSystemMessage},
 * {@code addPlayerMessage}) ends up in the one private {@code addMessage} hooked here, so whichever
 * one Baritone uses is covered.
 */
@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void mineman$suppressBaritoneCommandFeedback(
            Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (BaritoneController.isSuppressingChatFeedback()) {
            ci.cancel();
        }
    }
}
