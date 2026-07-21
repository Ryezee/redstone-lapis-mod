package com.example.redstonelapismod.network;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "I started (true) / stopped (false) holding jump while
 * elytra-gliding in powered rocket boots." Sent only on state CHANGES, not
 * every tick — the server keeps the flag per player and applies thrust each
 * tick on its side while it's on (draining charge, spawning the exhaust
 * trail for everyone, refreshing the fall-damage grace window).
 */
public record RocketThrustPayload(boolean thrusting) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RocketThrustPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "rocket_thrust"));

    /** One boolean on the wire: composite(field codec, getter, constructor). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RocketThrustPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RocketThrustPayload::thrusting,
                    RocketThrustPayload::new);

    @Override
    public CustomPacketPayload.Type<RocketThrustPayload> type() {
        return TYPE;
    }
}
