package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Tells the client HOW to draw each of our entity types. Registration-time
 * event (mod bus, client only) — same auto-subscribe mechanism as
 * {@link PowerHudOverlay}.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class ModEntityRenderers {

    private ModEntityRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ThrownItemRenderer draws the entity as its item's sprite (snowball
        // style). scale 1.0; fullBright=true — the rocket glows at max
        // brightness even in pitch-black caves.
        event.registerEntityRenderer(RedstoneLapisMod.REDSTONE_ROCKET_ENTITY.get(),
                context -> new ThrownItemRenderer<>(context, 1.0F, true));
    }
}
