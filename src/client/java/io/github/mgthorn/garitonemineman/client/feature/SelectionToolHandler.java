package io.github.mgthorn.garitonemineman.client.feature;

import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Repurposes a configurable item (default: Flint, see {@code selectionToolItem}) as a single-selection
 * area tool, the same way Litematica repurposes a stick: left click sets pos1, right click sets pos2.
 * Only one selection (pos1/pos2) ever exists. Uses its own long-range raytrace (bound via the
 * selectionSetPos1/2 hotkeys, default BUTTON_1/BUTTON_2) instead of vanilla block-interaction events,
 * so it isn't limited by survival reach distance. Alt + scroll while holding the tool moves the
 * last-selected corner along the direction the player is looking, instead of changing the hotbar slot.
 */
public class SelectionToolHandler {
    private static final double MAX_DISTANCE = 1024.0;
    private enum Corner { POS1, POS2 }

    private static final Color4f COLOR_POS1 = new Color4f(1f, 0.15f, 0.15f, 1f);
    private static final Color4f COLOR_POS2 = new Color4f(0.15f, 0.4f, 1f, 1f);
    private static final Color4f COLOR_EDGE_X = new Color4f(1f, 0.15f, 0.15f, 1f);
    private static final Color4f COLOR_EDGE_Y = new Color4f(0.15f, 1f, 0.15f, 1f);
    private static final Color4f COLOR_EDGE_Z = new Color4f(0.3f, 0.5f, 1f, 1f);
    private static final float SELECTION_LINE_WIDTH = 2f;
    private static final Color4f COLOR_WALLS = new Color4f(1f, 1f, 1f, 0.2f);
    private static final Color4f GLOW_POS1 = new Color4f(1f, 0.15f, 0.15f, 0.4f);
    private static final Color4f GLOW_POS2 = new Color4f(0.15f, 0.4f, 1f, 0.4f);

    @Nullable private static BlockPos pos1;
    @Nullable private static BlockPos pos2;
    @Nullable private static Corner lastSelectedCorner;

    /** Adds the selection's outline (one color per axis, visible through walls) to {@link MinemanWorldRenderer}'s batch. */
    public static boolean addAreaOutline(BufferBuilder buffer, Vec3 cameraPos) {
        if (pos1 == null || pos2 == null) {
            return false;
        }

        float minX = (float) (Math.min(pos1.getX(), pos2.getX()) - cameraPos.x);
        float minY = (float) (Math.min(pos1.getY(), pos2.getY()) - cameraPos.y);
        float minZ = (float) (Math.min(pos1.getZ(), pos2.getZ()) - cameraPos.z);
        float maxX = (float) (Math.max(pos1.getX(), pos2.getX()) + 1 - cameraPos.x);
        float maxY = (float) (Math.max(pos1.getY(), pos2.getY()) + 1 - cameraPos.y);
        float maxZ = (float) (Math.max(pos1.getZ(), pos2.getZ()) + 1 - cameraPos.z);

        // The four edges along each axis
        for (int i = 0; i < 4; i++) {
            float y = (i & 1) == 0 ? minY : maxY;
            float z = (i & 2) == 0 ? minZ : maxZ;
            addLine(buffer, minX, y, z, maxX, y, z, COLOR_EDGE_X);
        }

        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? minX : maxX;
            float z = (i & 2) == 0 ? minZ : maxZ;
            addLine(buffer, x, minY, z, x, maxY, z, COLOR_EDGE_Y);
        }

        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? minX : maxX;
            float y = (i & 2) == 0 ? minY : maxY;
            addLine(buffer, x, y, minZ, x, y, maxZ, COLOR_EDGE_Z);
        }

        return true;
    }

    private static void addLine(BufferBuilder buffer, float x1, float y1, float z1, float x2, float y2, float z2, Color4f color) {
        buffer.addVertex(x1, y1, z1).setColor(color.r, color.g, color.b, color.a).setLineWidth(SELECTION_LINE_WIDTH);
        buffer.addVertex(x2, y2, z2).setColor(color.r, color.g, color.b, color.a).setLineWidth(SELECTION_LINE_WIDTH);
    }

    /** Adds the pos1/pos2 block outlines (hidden behind blocks) to {@link MinemanWorldRenderer}'s batch. */
    public static boolean addCornerOutlines(BufferBuilder buffer) {
        if (pos1 != null) {
            RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos1, COLOR_POS1, 0.002, SELECTION_LINE_WIDTH, buffer);
        }

        if (pos2 != null) {
            RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos2, COLOR_POS2, 0.002, SELECTION_LINE_WIDTH, buffer);
        }

        return pos1 != null || pos2 != null;
    }

    /**
     * Renders the selection's translucent walls and the last-selected corner's glow. Left to MaLiLib, since
     * it picks the depth pipeline depending on whether the camera is inside the box. At most two draws per frame.
     */
    public static void renderSides() {
        if (pos1 != null && pos2 != null) {
            RenderUtils.renderAreaSides(pos1, pos2, COLOR_WALLS);
        }

        BlockPos lastPos = lastSelectedCorner == Corner.POS1 ? pos1 : lastSelectedCorner == Corner.POS2 ? pos2 : null;

        if (lastPos != null) {
            Color4f glowColor = lastSelectedCorner == Corner.POS1 ? GLOW_POS1 : GLOW_POS2;
            RenderUtils.renderAreaSides(lastPos, lastPos, glowColor);
        }
    }

    /**
     * Called from the selectionSetPos1 hotkey callback. Returns true if the click should be
     * consumed (i.e. the player was holding flint), so the caller cancels the underlying click.
     */
    public static boolean trySetPos1(Player player) {
        if (isHoldingFlint(player) == false) {
            return false;
        }

        setPos1FromLookRay(player);
        return true;
    }

    /**
     * Called from the selectionSetPos2 hotkey callback. Returns true if the click should be
     * consumed (i.e. the player was holding flint), so the caller cancels the underlying click.
     */
    public static boolean trySetPos2(Player player) {
        if (isHoldingFlint(player) == false) {
            return false;
        }

        setPos2FromLookRay(player);
        return true;
    }

    /**
     * Sets pos1 from the player's current look direction regardless of held item — used by the
     * GUI selection buttons, which are an explicit action and shouldn't require holding the tool.
     */
    public static boolean setPos1FromLookRay(Player player) {
        BlockPos hit = rayTrace(player);

        if (hit != null) {
            pos1 = hit;
            lastSelectedCorner = Corner.POS1;
        }

        return hit != null;
    }

    /**
     * Sets pos2 from the player's current look direction regardless of held item — used by the
     * GUI selection buttons, which are an explicit action and shouldn't require holding the tool.
     */
    public static boolean setPos2FromLookRay(Player player) {
        BlockPos hit = rayTrace(player);

        if (hit != null) {
            pos2 = hit;
            lastSelectedCorner = Corner.POS2;
        }

        return hit != null;
    }

    private static boolean isHoldingFlint(Player player) {
        Item tool = resolveToolItem();
        return player.getMainHandItem().is(tool) || player.getOffhandItem().is(tool);
    }

    private static Item resolveToolItem() {
        Identifier id = Identifier.tryParse(Configs.Generic.SELECTION_TOOL_ITEM.getStringValue().trim());
        return id != null ? BuiltInRegistries.ITEM.getOptional(id).orElse(Items.FLINT) : Items.FLINT;
    }

    public static String getToolDisplayName() {
        return resolveToolItem().getDefaultInstance().getHoverName().getString();
    }

    @Nullable
    private static BlockPos rayTrace(Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(MAX_DISTANCE));
        BlockHitResult result = player.level().clip(
                new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK ? result.getBlockPos() : null;
    }

    /**
     * Called from the mouse-scroll mixin. Returns true if the scroll was consumed (holding flint + alt),
     * meaning the caller should cancel the vanilla hotbar-scroll handling.
     */
    public static boolean handleScroll(double verticalAmount) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.gui.screen() != null) {
            return false;
        }

        if (isHoldingFlint(player) == false || GuiBase.isAltDown() == false) {
            return false;
        }

        if (lastSelectedCorner != null) {
            BlockPos current = lastSelectedCorner == Corner.POS1 ? pos1 : pos2;

            if (current != null) {
                Direction direction = getFacingDirection(player);
                int amount = verticalAmount > 0 ? 1 : -1;
                BlockPos moved = current.relative(direction, amount);

                if (lastSelectedCorner == Corner.POS1) {
                    pos1 = moved;
                }
                else {
                    pos2 = moved;
                }
            }
        }

        return true;
    }

    private static Direction getFacingDirection(Player player) {
        Vec3 look = player.getLookAngle();
        double ax = Math.abs(look.x);
        double ay = Math.abs(look.y);
        double az = Math.abs(look.z);

        if (ay >= ax && ay >= az) {
            return look.y > 0 ? Direction.UP : Direction.DOWN;
        }
        else if (ax >= az) {
            return look.x > 0 ? Direction.EAST : Direction.WEST;
        }
        else {
            return look.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    @Nullable
    public static BlockPos getPos1() {
        return pos1;
    }

    @Nullable
    public static BlockPos getPos2() {
        return pos2;
    }

    public static void clearSelection() {
        pos1 = null;
        pos2 = null;
        lastSelectedCorner = null;
    }
}
