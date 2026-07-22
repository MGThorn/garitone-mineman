package com.example.client.mixin;

import com.example.client.gui.GuiMinemanMain;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
    protected MixinTitleScreen() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mineman$addButton(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(Component.literal("Mineman"), btn ->
                GuiBase.openGui(new GuiMinemanMain(this))
            ).bounds(6, 6, 80, 20).build()
        );
    }
}
