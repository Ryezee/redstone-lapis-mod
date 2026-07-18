package com.example.redstonelapismod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RedstoneLapisMod.MODID)
public class RedstoneLapisMod {
    // The mod id — must match the "mod_id" in build.gradle and the assets/data namespace.
    public static final String MODID = "redstonelapismod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred register for this mod's items.
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // Redstone Goggles — a head-slot wearable that grants night vision in the dark.
    public static final DeferredItem<Item> REDSTONE_GOGGLES = ITEMS.register("redstone_goggles",
            () -> new GogglesItem(new Item.Properties().stacksTo(1)));

    public RedstoneLapisMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the deferred registers to the mod event bus.
        ITEMS.register(modEventBus);

        // Place our items into a vanilla creative tab.
        modEventBus.addListener(this::addCreative);

        // Grant night vision while the goggles are worn (game/server event bus).
        NeoForge.EVENT_BUS.addListener(GogglesHandler::onPlayerTick);

        LOGGER.info("Redstone & Lapis Mod loaded");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(REDSTONE_GOGGLES);
        }
    }
}
