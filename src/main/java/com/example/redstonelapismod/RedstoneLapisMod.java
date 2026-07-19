package com.example.redstonelapismod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
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

    // Deferred register for this mod's data component types (typed per-ItemStack data, 1.20.5+).
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    // "charge" — how much redstone power an item currently stores (used by the battery).
    // persistent(...) = how it's written to disk; networkSynchronized(...) = how it's sent to clients.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE =
            DATA_COMPONENTS.registerComponentType("charge", builder -> builder
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    // Redstone Goggles — a head-slot wearable that grants night vision in the dark.
    public static final DeferredItem<Item> REDSTONE_GOGGLES = ITEMS.register("redstone_goggles",
            () -> new GogglesItem(new Item.Properties().stacksTo(1)));

    // Redstone Goggle Lens — expensive crafting component; two are needed for the goggles.
    public static final DeferredItem<Item> REDSTONE_GOGGLE_LENS = ITEMS.register("redstone_goggle_lens",
            () -> new Item(new Item.Properties()));

    // Redstone Battery — portable, reusable charge storage that powers redstone gadgets.
    public static final DeferredItem<Item> REDSTONE_BATTERY = ITEMS.register("redstone_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1)));

    public RedstoneLapisMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the deferred registers to the mod event bus.
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);

        // Place our items into a vanilla creative tab.
        modEventBus.addListener(this::addCreative);

        // Grant night vision while the goggles are worn (game/server event bus).
        NeoForge.EVENT_BUS.addListener(GogglesHandler::onPlayerTick);

        LOGGER.info("Redstone & Lapis Mod loaded");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(REDSTONE_GOGGLES);
            event.accept(REDSTONE_BATTERY);                                  // empty battery
            event.accept(BatteryItem.fullyCharged(REDSTONE_BATTERY.get()));  // pre-charged, for testing
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(REDSTONE_GOGGLE_LENS);
        }
    }
}
