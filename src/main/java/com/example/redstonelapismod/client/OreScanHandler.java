package com.example.redstonelapismod.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * Client-side ore "pulse scan" for the Redstone Goggles.
 *
 * <p>Pressing the scan key (see {@link OreScanKeys}) while the goggles are worn captures every ore
 * block within a radius and renders a fading, through-wall outline around each. The whole feature is
 * visual and reads only blocks the client already has loaded, so there is no networking and nothing
 * runs on the server. Night vision (the goggles' other ability) is handled separately and server-side
 * in {@code GogglesHandler}.
 *
 * <p>Ores are matched generically through the {@code c:ores} tag ({@link Tags.Blocks#ORES}) so any
 * ore-adding mod in the pack is covered, and each outline is tinted by that block's map color so
 * different ores read differently for free — modded ores included.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class OreScanHandler {
    /** Sphere radius (blocks) scanned around the player. */
    private static final int SCAN_RADIUS = 16;
    /** Safety cap on highlighted ores per scan, to bound the render/scan cost. */
    private static final int MAX_HITS = 400;
    /** How long a scan's highlights linger and fade, in milliseconds. */
    private static final long FADE_MS = 6000L;

    private static final List<OreHit> HITS = new ArrayList<>();
    private static long scanStartMs = Long.MIN_VALUE;

    private OreScanHandler() {}

    /** One highlighted ore: its position and an RGB color (0xRRGGBB) taken from the block's map color. */
    private record OreHit(BlockPos pos, int color) {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (OreScanKeys.ORE_SCAN.consumeClick()) {
            performScan();
        }
    }

    private static void performScan() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean wearing = player.getItemBySlot(EquipmentSlot.HEAD)
                .is(RedstoneLapisMod.REDSTONE_GOGGLES.get());
        if (!wearing) {
            return;
        }

        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = SCAN_RADIUS;
        int rSq = r * r;

        HITS.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) {
                        continue; // keep the scan spherical
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor.getY())) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Tags.Blocks.ORES)) {
                        int color = state.getMapColor(level, cursor).col;
                        HITS.add(new OreHit(cursor.immutable(), color));
                        if (HITS.size() >= MAX_HITS) {
                            break outer;
                        }
                    }
                }
            }
        }

        scanStartMs = Util.getMillis();
        // A short "ping" so a scan feels tactile even when nothing is nearby.
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (HITS.isEmpty()) {
            return;
        }

        long elapsed = Util.getMillis() - scanStartMs;
        if (elapsed >= FADE_MS) {
            HITS.clear();
            return;
        }
        float alpha = 1.0f - (elapsed / (float) FADE_MS);

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(OreRenderType.ORE_LINES);

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (OreHit hit : HITS) {
            BlockPos p = hit.pos();
            float red = ((hit.color() >> 16) & 0xFF) / 255.0f;
            float green = ((hit.color() >> 8) & 0xFF) / 255.0f;
            float blue = (hit.color() & 0xFF) / 255.0f;
            LevelRenderer.renderLineBox(pose, consumer,
                    p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0,
                    red, green, blue, alpha);
        }
        pose.popPose();
        buffers.endBatch(OreRenderType.ORE_LINES);
    }
}
