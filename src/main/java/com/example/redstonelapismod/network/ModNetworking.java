package com.example.redstonelapismod.network;

import com.example.redstonelapismod.RedstoneLapisMod;
import com.example.redstonelapismod.RocketBootsHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers this mod's custom network payloads. Runs on BOTH physical sides
 * (no Dist restriction!) — client and dedicated server must both know every
 * payload's codec or the connection handshake refuses. The event is a mod-bus
 * event; FML routes it there automatically from the annotation.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID)
public final class ModNetworking {
    private ModNetworking() {}

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // "1" = our wire-format version; bump if a payload's fields ever change.
        PayloadRegistrar registrar = event.registrar("1");

        // Handlers run on the server MAIN thread by default (HandlerThread.MAIN),
        // so touching the player directly is safe — no enqueueWork needed.
        registrar.playToServer(RocketBlastPayload.TYPE, RocketBlastPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RocketBootsHandler.handleBlast(player, payload.chargeFraction(), payload.gliding());
                    }
                });
    }
}
