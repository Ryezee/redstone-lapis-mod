package com.example.redstonelapismod.client;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom line {@link RenderType} for the ore pulse-scan highlights.
 *
 * <p>Extending {@link RenderType} (which extends {@link RenderStateShard}) is the
 * standard trick that lets us reference the {@code protected} render-state shards
 * like {@link #NO_DEPTH_TEST}. The type is never actually instantiated — we only
 * use it as a namespace for {@link #ORE_LINES}.
 *
 * <p>{@code NO_DEPTH_TEST} is what makes the outlines draw <em>through walls</em>,
 * and drawing them as ordinary world line geometry (rather than touching the
 * lightmap) keeps them compatible with shader packs like Photon.
 */
public final class OreRenderType extends RenderType {
    private OreRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException("OreRenderType is a shard namespace and is never instantiated");
    }

    public static final RenderType ORE_LINES = RenderType.create(
            "redstonelapismod:ore_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                    .setLayeringState(NO_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
