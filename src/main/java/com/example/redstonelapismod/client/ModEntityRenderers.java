package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Register the launcher's box-tree geometry; baked at resource load.
        event.registerLayerDefinition(RocketLauncherModel.LAYER, RocketLauncherModel::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // Bind the custom 3D renderer to the launcher item. The model JSON's
        // builtin/entity parent routes drawing here in every view context.
        event.registerItem(new IClientItemExtensions() {
            private RocketLauncherRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new RocketLauncherRenderer(); // lazy: Minecraft is fully alive by first render
                }
                return renderer;
            }
        }, RedstoneLapisMod.REDSTONE_ROCKET_LAUNCHER.get());
    }
}
