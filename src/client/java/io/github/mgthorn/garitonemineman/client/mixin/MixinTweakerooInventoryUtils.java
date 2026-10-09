package io.github.mgthorn.garitonemineman.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.tweakeroo.util.InventoryUtils;
import io.github.mgthorn.garitonemineman.client.feature.ToolSwitchBackHandler;

/**
 * Marks the Tweakeroo entry points that move tools and weapons around on left-click, so
 * {@link ToolSwitchBackHandler} can tell tweakToolSwitch's and tweakWeaponSwitch's inventory clicks
 * apart from tweakSwapAlmostBrokenTools' and everyone else's.
 */
@Mixin(value = InventoryUtils.class, remap = false)
public class MixinTweakerooInventoryUtils {
    @Inject(method = "trySwitchToEffectiveTool", at = @At("HEAD"))
    private static void mineman$toolSwitchPre(CallbackInfo ci) {
        ToolSwitchBackHandler.onToolSwitchPre();
    }

    @Inject(method = "trySwitchToEffectiveTool", at = @At("RETURN"))
    private static void mineman$toolSwitchPost(CallbackInfo ci) {
        ToolSwitchBackHandler.onToolSwitchPost();
    }

    @Inject(method = "trySwitchToWeapon", at = @At("HEAD"))
    private static void mineman$weaponSwitchPre(CallbackInfo ci) {
        ToolSwitchBackHandler.onWeaponSwitchPre();
    }

    @Inject(method = "trySwitchToWeapon", at = @At("RETURN"))
    private static void mineman$weaponSwitchPost(CallbackInfo ci) {
        ToolSwitchBackHandler.onWeaponSwitchPost();
    }

    @Inject(method = "trySwapCurrentToolIfNearlyBroken()V", at = @At("HEAD"))
    private static void mineman$durabilitySwapPre(CallbackInfo ci) {
        ToolSwitchBackHandler.onDurabilitySwapPre();
    }

    @Inject(method = "trySwapCurrentToolIfNearlyBroken()V", at = @At("RETURN"))
    private static void mineman$durabilitySwapPost(CallbackInfo ci) {
        ToolSwitchBackHandler.onDurabilitySwapPost();
    }
}
