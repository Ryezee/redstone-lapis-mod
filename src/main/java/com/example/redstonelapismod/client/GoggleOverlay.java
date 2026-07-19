package com.example.redstonelapismod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The Redstone Goggles' visor overlay: a faint red wash plus animated "grain" drawn over the whole
 * screen while the goggles are worn, so the world reads like you're looking through a device.
 *
 * <p>This is a <b>HUD layer</b> — a 2D pass that runs <i>after</i> the 3D world is already rendered.
 * That's what makes it shader-safe: Iris/Photon has finished painting the scene into the frame by the
 * time we draw here, so we're just compositing on top and can never conflict with it. (The alternative,
 * a post-processing shader, would fight Photon for the same pipeline — so we deliberately avoid it.)
 *
 * <p>Registered on {@link RegisterGuiLayersEvent}, a <b>mod-bus</b> event fired once during client
 * setup — think of it as "declare your HUD element" — via {@code registerBelowAll} so our tint sits
 * beneath the vanilla HUD (hearts, hotbar, chat stay crisp and readable on top).
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class GoggleOverlay {
    // --- Tunables (named constants so the "feel" is a one-number tweak, promotable to config later) ---

    /** Whole-screen red tint. ARGB: 0xAARRGGBB — low alpha keeps it subtle so it won't fatigue the eye. */
    private static final int RED_TINT_ARGB = 0x22B4141E;
    /** Overall strength of the grain layer (0 = invisible, 1 = full). */
    private static final float GRAIN_ALPHA = 0.15f;
    /** Edge length of the (tileable) grain texture, in GUI pixels. */
    private static final int GRAIN_TILE = 128;
    /** How often the grain reshuffles, in ms — larger = calmer static, smaller = busier shimmer. */
    private static final long GRAIN_STEP_MS = 55L;

    /** The grain texture, at assets/redstonelapismod/textures/gui/goggle_grain.png. */
    private static final ResourceLocation GRAIN_TEX =
            ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "textures/gui/goggle_grain.png");
    /** Unique id for our HUD layer (namespaced so it can't clash with another mod's layer). */
    private static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "goggle_overlay");

    private GoggleOverlay() {}

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        // registerBelowAll -> drawn above the world but below vanilla HUD elements.
        event.registerBelowAll(OVERLAY_ID, GoggleOverlay::render);
    }

    /** Called every frame for our layer; a no-op unless the goggles are actually on. */
    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!GogglesClient.isPowered(mc.player) || mc.options.hideGui) {
            return; // goggles off or battery dead, or the player hid the HUD (F1)
        }

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        // 1) A flat red wash over everything — no texture needed, just a translucent rectangle.
        graphics.fill(0, 0, w, h, RED_TINT_ARGB);

        // 2) Animated grain: tile the noise texture across the screen, nudging its origin over time
        //    so the static "crawls". frame advances one step every GRAIN_STEP_MS.
        long frame = Util.getMillis() / GRAIN_STEP_MS;
        int ox = -(int) (frame * 7 % GRAIN_TILE);   // start off-screen (negative) so tiles fill the edges
        int oy = -(int) (frame * 13 % GRAIN_TILE);

        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, GRAIN_ALPHA);  // tints the blit; alpha here = grain strength
        for (int y = oy; y < h; y += GRAIN_TILE) {
            for (int x = ox; x < w; x += GRAIN_TILE) {
                graphics.blit(GRAIN_TEX, x, y, 0.0f, 0.0f, GRAIN_TILE, GRAIN_TILE, GRAIN_TILE, GRAIN_TILE);
            }
        }
        graphics.setColor(1f, 1f, 1f, 1f);           // reset so we don't tint the rest of the HUD
        RenderSystem.disableBlend();
    }
}
