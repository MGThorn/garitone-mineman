package io.github.mgthorn.garitonemineman.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import io.github.mgthorn.garitonemineman.client.gui.EntityIconRenderer;

/**
 * Lets {@link EntityIconRenderer} draw its entity icons with its own renderers, one per entity type,
 * instead of vanilla's single shared entity renderer.
 */
@Mixin(GuiRenderer.class)
public abstract class MixinGuiRenderer {
    @Shadow @Final private GuiRenderState renderState;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(method = "preparePictureInPictureState", at = @At("HEAD"), cancellable = true)
    private void mineman$prepareEntityIcon(PictureInPictureRenderState state, int guiScale, CallbackInfo ci) {
        if (EntityIconRenderer.prepare(state, this.renderState, this.featureRenderDispatcher, guiScale)) {
            ci.cancel();
        }
    }
}
