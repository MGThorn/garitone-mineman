package com.example.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.client.config.Configs;

@Mixin(Gui.class)
public class MixinGui {
    @Unique
    private static final Identifier MINEMAN$PUMPKIN_BLUR = Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png");

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void mineman$suppressPumpkinOverlay(GuiGraphics guiGraphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (Configs.Generic.DISABLE_PUMPKIN_OVERLAY.getBooleanValue() && MINEMAN$PUMPKIN_BLUR.equals(texture)) {
            ci.cancel();
        }
    }
}
