package com.example.client.feature;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

/**
 * Repurposes the Flint item as a single-selection area tool, the same way Litematica repurposes
 * a stick: left click sets pos1, right click sets pos2. Only one selection (pos1/pos2) ever exists.
 * Uses its own long-range raytrace (bound via the selectionSetPos1/2 hotkeys, default BUTTON_1/BUTTON_2)
 * instead of vanilla block-interaction events, so it isn't limited by survival reach distance.
 * Alt + scroll while holding flint moves the last-selected corner along the direction the player
 * is looking, instead of changing the hotbar slot.
 */
public class SelectionToolHandler {
    private static final double MAX_DISTANCE = 1024.0;
    private enum Corner { POS1, POS2 }

    private static final Color4f COLOR_POS1 = new Color4f(1f, 0.15f, 0.15f, 1f);
    private static final Color4f COLOR_POS2 = new Color4f(0.15f, 0.4f, 1f, 1f);
    private static final Color4f COLOR_EDGE_X = new Color4f(1f, 0.15f, 0.15f, 1f);
    private static final Color4f COLOR_EDGE_Y = new Color4f(0.15f, 1f, 0.15f, 1f);
    private static final Color4f COLOR_EDGE_Z = new Color4f(0.3f, 0.5f, 1f, 1f);
    private static final Color4f COLOR_WALLS = new Color4f(1f, 1f, 1f, 0.2f);
    private static final Color4f GLOW_POS1 = new Color4f(1f, 0.15f, 0.15f, 0.4f);
    private static final Color4f GLOW_POS2 = new Color4f(0.15f, 0.4f, 1f, 0.4f);

    @Nullable private static BlockPos pos1;
    @Nullable private static BlockPos pos2;
    @Nullable private static Corner lastSelectedCorner;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (pos1 != null && pos2 != null) {
                RenderUtils.renderAreaOutline(pos1, pos2, 2f, COLOR_EDGE_X, COLOR_EDGE_Y, COLOR_EDGE_Z);
                RenderUtils.renderAreaSides(pos1, pos2, COLOR_WALLS, ctx.matrices().last().pose());
            }

            if (pos1 != null) {
                RenderUtils.renderBlockOutline(pos1, 0.002f, 2f, COLOR_POS1);
            }

            if (pos2 != null) {
                RenderUtils.renderBlockOutline(pos2, 0.002f, 2f, COLOR_POS2);
            }

            BlockPos lastPos = lastSelectedCorner == Corner.POS1 ? pos1 : lastSelectedCorner == Corner.POS2 ? pos2 : null;

            if (lastPos != null) {
                Color4f glowColor = lastSelectedCorner == Corner.POS1 ? GLOW_POS1 : GLOW_POS2;
                RenderUtils.renderAreaSides(lastPos, lastPos, glowColor, ctx.matrices().last().pose());
            }
        });
    }

    /**
     * Called from the selectionSetPos1 hotkey callback. Returns true if the click should be
     * consumed (i.e. the player was holding flint), so the caller cancels the underlying click.
     */
    public static boolean trySetPos1(Player player) {
        if (isHoldingFlint(player) == false) {
            return false;
        }

        BlockPos hit = rayTrace(player);

        if (hit != null) {
            pos1 = hit;
            lastSelectedCorner = Corner.POS1;
        }

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

        BlockPos hit = rayTrace(player);

        if (hit != null) {
            pos2 = hit;
            lastSelectedCorner = Corner.POS2;
        }

        return true;
    }

    private static boolean isHoldingFlint(Player player) {
        return player.getMainHandItem().is(Items.FLINT) || player.getOffhandItem().is(Items.FLINT);
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

        if (player == null || mc.screen != null) {
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
