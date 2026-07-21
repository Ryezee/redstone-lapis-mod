package com.example.redstonelapismod.network;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "I pressed jump in mid-air wearing powered rocket boots and
 * launched myself." Carries no data — the sender's identity comes with the
 * connection, and everything else (worn boots, charge) the server checks
 * itself. The client has ALREADY applied the velocity locally when this
 * arrives (player movement is client-authoritative); the server's job is to
 * bill the battery, remember the fall-damage grace window, and show the
 * launch to everyone nearby.
 */
public record RocketJumpPayload() implements CustomPacketPayload {

    /** The single instance — an empty record needs no per-send allocation. */
    public static final RocketJumpPayload INSTANCE = new RocketJumpPayload();

    /** Wire id of this payload type, namespaced to our mod. */
    public static final CustomPacketPayload.Type<RocketJumpPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "rocket_jump"));

    /** Zero-byte codec: encode writes nothing, decode returns the singleton. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RocketJumpPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<RocketJumpPayload> type() {
        return TYPE;
    }
}
