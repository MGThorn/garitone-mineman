package io.github.mgthorn.garitonemineman.client.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import io.github.mgthorn.garitonemineman.client.compat.ItemScrollerCompat;
import io.github.mgthorn.garitonemineman.client.compat.TweakerooCompat;

/**
 * Skips the Item Scroller and Tweakeroo mixins when those mods are not installed, so both are optional
 * dependencies.
 */
public class MinemanMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "io.github.mgthorn.garitonemineman.client.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(MIXIN_PACKAGE.length());

        if (name.startsWith("MixinItemScroller") || name.startsWith("ItemScroller")) {
            return ItemScrollerCompat.isLoaded();
        }

        if (name.startsWith("MixinTweakeroo")) {
            return TweakerooCompat.isLoaded();
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
