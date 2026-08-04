package com.example.client.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.client.compat.baritone.BaritoneController;

/**
 * Every Baritone command (#mine, #cancel, #goal, #path, #sel, #set, ...) gets echoed back
 * ("> #command") and a result printed ("Successfully set X to Y", etc.) — none of it gated by any
 * Baritone setting, confirmed by disassembling the bundled jar's command classes. Tracing
 * Baritone's {@code Settings.logger} default consumer (bytecode, not guessed) showed it always
 * calls {@code ChatComponent.addMessage(Component, MessageSignature, GuiMessageTag)} — the
 * three-argument overload — never the single-argument one, for both the echo and the confirmation.
 * With {@link com.example.client.config.Configs.Generic#HIDE_BARITONE_CHAT_FEEDBACK} enabled, this
 * drops any chat message while {@link BaritoneController#isSuppressingChatFeedback()} reports one of
 * this mod's own commands was just sent (a short window opened in {@code BaritoneController#sendChat}).
 * Both overloads are hooked — the three-argument one for Baritone's actual path, the single-argument
 * one as a defensive fallback in case anything else routes through it during the same window.
 */
@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void mineman$suppressBaritoneCommandFeedback(Component message, CallbackInfo ci) {
        if (BaritoneController.isSuppressingChatFeedback()) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void mineman$suppressBaritoneCommandFeedbackTagged(
            Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (BaritoneController.isSuppressingChatFeedback()) {
            ci.cancel();
        }
    }
}
