package com.example.redstonelapismod.network;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "I released a charged redstone blast." Carries how full
 * the charge was (0.33..1.0 — the client already applied the motion locally;
 * movement is client-authoritative) and whether it was the gliding (forward)
 * or grounded/air (upward) variant. The server clamps the fraction, bills the
 * battery proportionally, mirrors the velocity for plausibility, grants the
 * fall-damage grace window, and broadcasts the explosion to everyone nearby.
 */
public record RocketBlastPayload(float chargeFraction, boolean gliding) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RocketBlastPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "rocket_blast"));

    /** Two fields on the wire: composite(codec, getter, codec, getter, ctor). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RocketBlastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, RocketBlastPayload::chargeFraction,
                    ByteBufCodecs.BOOL, RocketBlastPayload::gliding,
                    RocketBlastPayload::new);

    @Override
    public CustomPacketPayload.Type<RocketBlastPayload> type() {
        return TYPE;
    }
}
