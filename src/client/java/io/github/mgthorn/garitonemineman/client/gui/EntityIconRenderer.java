package io.github.mgthorn.garitonemineman.client.gui;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.GaritoneMineman;

/**
 * Draws entity models as GUI icons, for {@link GuiEntityEspEditor}.
 * <p>
 * Vanilla has a single {@link GuiEntityRenderer} with a single texture, so with several entities in one
 * frame they would all show the last one drawn. Like vanilla does for oversized items, every entity type
 * (and icon size) gets its own renderer here (picked by {@code MixinGuiRenderer}), and since the icons don't animate, each
 * one is drawn into its texture only once and then just blitted.
 * <p>
 * Models need a world to create the entity in, so without one {@link #draw} returns false.
 */
public final class EntityIconRenderer {
    private EntityIconRenderer() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    // One renderer per type and icon size, so the grid and the list don't keep redrawing each other's texture
    private static final Map<RendererKey, IconRenderer> RENDERERS = new HashMap<>();
    // States submitted this frame, mapped to the renderer that has to draw them
    private static final Map<PictureInPictureRenderState, IconRenderer> PENDING = new IdentityHashMap<>();
    // Empty when the type can't be drawn as a model
    private static final Map<EntityType<?>, Optional<EntityRenderState>> RENDER_STATES = new HashMap<>();
    @Nullable private static ClientLevel cachedLevel;
    private static int nextFakeId = -1_000_000;

    /**
     * Adds the model of {@code type} centered in the given square.
     * Returns false if it can't be drawn (no world, or an entity without a model), so the caller can draw a fallback.
     */
    public static boolean draw(GuiContext ctx, EntityType<?> type, int x, int y, int size) {
        EntityRenderState renderState = getRenderState(type);

        if (renderState == null) {
            return false;
        }

        float height = renderState.boundingBoxHeight;
        float width = renderState.boundingBoxWidth;
        float scale = size * 0.9f / Math.max(0.1f, Math.max(height, width * 1.2f));
        Vector3f translation = new Vector3f(0, height / 2, 0);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);

        GuiEntityRenderState state = new GuiEntityRenderState(renderState, translation, rotation, new Quaternionf(),
                x, y, x + size, y + size, scale, ctx.peekLastScissor());
        PENDING.put(state, RENDERERS.computeIfAbsent(new RendererKey(type, size),
                k -> new IconRenderer(Minecraft.getInstance().getEntityRenderDispatcher())));
        ctx.addSpecialElement(state);
        return true;
    }

    /** Called from {@code MixinGuiRenderer}: draws the state with its own renderer, if it's one of ours. */
    public static boolean prepare(PictureInPictureRenderState state, GuiRenderState guiRenderState,
                                  FeatureRenderDispatcher featureRenderDispatcher, int guiScale) {
        IconRenderer renderer = PENDING.remove(state);

        if (renderer == null) {
            return false;
        }

        renderer.prepare((GuiEntityRenderState) state, guiRenderState, featureRenderDispatcher, guiScale);
        return true;
    }

    /** Frees the textures and the cached entities. Called when the editor closes. */
    public static void clear() {
        RENDERERS.values().forEach(IconRenderer::close);
        RENDERERS.clear();
        PENDING.clear();
        RENDER_STATES.clear();
        cachedLevel = null;
    }

    @Nullable
    private static EntityRenderState getRenderState(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return null;
        }

        if (mc.level != cachedLevel) {
            clear();
            cachedLevel = mc.level;
        }

        return RENDER_STATES.computeIfAbsent(type, t -> Optional.ofNullable(createRenderState(mc, t))).orElse(null);
    }

    @Nullable
    private static EntityRenderState createRenderState(Minecraft mc, EntityType<?> type) {
        try {
            // Players can't be created from their type, so the own player stands in for them
            Entity entity = type == EntityTypes.PLAYER ? mc.player : type.create(mc.level, EntitySpawnReason.LOAD);

            if (entity == null) {
                return null;
            }

            // Entities only get an ID when added to a world, but rendering (held items, for one) needs it.
            // Negative IDs can't clash with real entities.
            if (entity != mc.player) {
                entity.setId(nextFakeId--);
            }

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.getRenderer(entity).createRenderState(entity, 1f);

            if (state.boundingBoxHeight <= 0 && state.boundingBoxWidth <= 0) {
                return null;
            }

            // Same tweaks as the inventory player preview, turned a bit for a three-quarter view
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.nameTag = null;
            state.lightCoords = FULL_BRIGHT;

            if (state instanceof LivingEntityRenderState living) {
                living.bodyRot = 150f;
                living.yRot = 0f;
                living.xRot = 0f;
                living.boundingBoxWidth /= living.scale;
                living.boundingBoxHeight /= living.scale;
                living.scale = 1f;
            }

            return state;
        }
        catch (Throwable e) {
            // Some entities need more context than a bare client world; they get the fallback icon
            GaritoneMineman.LOGGER.warn("Entity ESP: can't draw a model for {}", type, e);
            return null;
        }
    }

    private record RendererKey(EntityType<?> type, int size) {}

    /** A {@link GuiEntityRenderer} that keeps its picture after the first draw. */
    private static class IconRenderer extends GuiEntityRenderer {
        private boolean rendered;

        IconRenderer(EntityRenderDispatcher dispatcher) {
            super(dispatcher);
        }

        @Override
        protected boolean textureIsReadyToBlit(GuiEntityRenderState state) {
            return this.rendered;
        }

        @Override
        protected void renderToTexture(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
            super.renderToTexture(state, poseStack, collector);
            this.rendered = true;
        }
    }
}
