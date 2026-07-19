package com.example.redstonelapismod.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3f;

/**
 * Derives a sparkle color for each ore by sampling its own texture: ore textures are mostly gray
 * stone plus colored specks, so a saturation-weighted average of the pixels recovers the speck
 * color (redstone red, lapis blue, a modded ore's whatever) while the gray background cancels out.
 *
 * <p>This works for any mod's ores with no configuration, because we read the same texture the
 * game renders. Results are memoized per {@link Block} — the pixel crunch happens once per ore
 * type per session, then it's a map lookup. If a texture can't be read or is entirely gray, we
 * fall back to a warm gold.
 */
public final class OreColors {
    /** Used when a texture can't be sampled or has no usable color: a neutral warm gold. */
    private static final Vector3f FALLBACK_GOLD = new Vector3f(1.0f, 0.85f, 0.4f);
    /** Minimum average saturation² for the speck-color path; below this the texture is "all gray". */
    private static final double MIN_ACCENT_SIGNAL = 0.02;

    private static final Map<Block, DustParticleOptions> CACHE = new HashMap<>();

    private OreColors() {}

    /** The dust particle for this ore, colored by its texture's accent. Cached per block type. */
    public static DustParticleOptions dustFor(BlockState state, float scale) {
        return CACHE.computeIfAbsent(state.getBlock(),
                block -> new DustParticleOptions(accentColorOf(state), scale));
    }

    private static Vector3f accentColorOf(BlockState state) {
        try {
            // The block's "particle icon" is the sprite vanilla uses for its break/sprint particles —
            // a good "representative texture" for any block, vanilla or modded.
            TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                    .getBlockModelShaper().getParticleIcon(state);
            // Sprite name ("minecraft:block/redstone_ore") -> the actual PNG's resource path.
            ResourceLocation name = sprite.contents().name();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                    name.getNamespace(), "textures/" + name.getPath() + ".png");
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResource(texture).orElseThrow();
            try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
                return extractAccent(image);
            }
        } catch (Exception e) {
            return FALLBACK_GOLD; // odd model/texture setup — never crash over a cosmetic color
        }
    }

    private static Vector3f extractAccent(NativeImage image) {
        // Two running averages: saturation²-weighted (finds the colored specks) and plain
        // (fallback for near-gray textures like coal ore).
        double weightedR = 0, weightedG = 0, weightedB = 0, weightSum = 0;
        double plainR = 0, plainG = 0, plainB = 0;
        int opaqueCount = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y); // despite the method name, bytes are A-B-G-R
                int alpha = (abgr >>> 24) & 0xFF;
                if (alpha < 128) {
                    continue; // skip transparent pixels entirely
                }
                int red = abgr & 0xFF;
                int green = (abgr >>> 8) & 0xFF;
                int blue = (abgr >>> 16) & 0xFF;

                plainR += red;
                plainG += green;
                plainB += blue;
                opaqueCount++;

                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                if (max == 0) {
                    continue; // pure black has no hue to contribute
                }
                // HSV saturation: 0 for gray, 1 for vivid. Squared so specks dominate the average.
                double saturation = (max - min) / (double) max;
                double weight = saturation * saturation;
                weightedR += red * weight;
                weightedG += green * weight;
                weightedB += blue * weight;
                weightSum += weight;
            }
        }

        double r, g, b;
        if (opaqueCount > 0 && weightSum / opaqueCount > MIN_ACCENT_SIGNAL) {
            r = weightedR / weightSum;
            g = weightedG / weightSum;
            b = weightedB / weightSum;
        } else if (opaqueCount > 0) {
            r = plainR / opaqueCount;
            g = plainG / opaqueCount;
            b = plainB / opaqueCount;
        } else {
            return FALLBACK_GOLD;
        }

        // Normalize so the brightest channel hits 1.0: keeps the hue but makes the mote vivid,
        // which matters against dark cave walls (and turns coal's dark gray into a silver glint).
        double max = Math.max(r, Math.max(g, b));
        return new Vector3f((float) (r / max), (float) (g / max), (float) (b / max));
    }
}
