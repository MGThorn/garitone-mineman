package com.example.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.client.feature.SelectionToolHandler;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mineman$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (SelectionToolHandler.handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}
