package com.example.redstonelapismod.client;

import org.lwjgl.glfw.GLFW;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Registers the client keybind that triggers the ore pulse-scan. Client-only and
 * on the mod event bus, since {@link RegisterKeyMappingsEvent} fires there.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class OreScanKeys {
    public static final String CATEGORY = "key.categories.redstonelapismod";

    public static final KeyMapping ORE_SCAN = new KeyMapping(
            "key.redstonelapismod.ore_scan",
            GLFW.GLFW_KEY_G,
            CATEGORY);

    private OreScanKeys() {}

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ORE_SCAN);
    }
}
