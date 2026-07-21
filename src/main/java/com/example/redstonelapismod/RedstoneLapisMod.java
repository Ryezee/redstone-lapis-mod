package com.example.redstonelapismod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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

    // Deferred register for armor materials (a registry since 1.20.5, like items/components).
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);

    // "goggles" armor material — zero protection; exists so the goggles render like a
    // fitted helmet using textures/models/armor/goggles_layer_1.png.
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GOGGLES_ARMOR_MATERIAL =
            ARMOR_MATERIALS.register("goggles", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 0),          // defense points: none
                    9,                                          // enchantability (leather-ish)
                    SoundEvents.ARMOR_EQUIP_LEATHER,            // equip sound
                    () -> Ingredient.of(Items.REDSTONE),        // anvil repair ingredient
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(MODID, "goggles"))),
                    0.0F,                                       // toughness
                    0.0F));                                     // knockback resistance

    // "headtorch" armor material — zero protection; renders via
    // textures/models/armor/headtorch_layer_1.png.
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEADTORCH_ARMOR_MATERIAL =
            ARMOR_MATERIALS.register("headtorch", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 0),
                    9,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.REDSTONE),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(MODID, "headtorch"))),
                    0.0F, 0.0F));

    // "rocket_boots" armor material — zero protection; renders via
    // textures/models/armor/rocket_boots_layer_1.png (boots draw from layer_1).
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ROCKET_BOOTS_ARMOR_MATERIAL =
            ARMOR_MATERIALS.register("rocket_boots", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.BOOTS, 0),
                    9,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.REDSTONE),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(MODID, "rocket_boots"))),
                    0.0F, 0.0F));

    // Redstone Goggles — a head-slot wearable that grants night vision in the dark.
    public static final DeferredItem<Item> REDSTONE_GOGGLES = ITEMS.register("redstone_goggles",
            () -> new GogglesItem(new Item.Properties().stacksTo(1)));

    // Redstone Goggle Lens — expensive crafting component; two are needed for the goggles.
    public static final DeferredItem<Item> REDSTONE_GOGGLE_LENS = ITEMS.register("redstone_goggle_lens",
            () -> new Item(new Item.Properties()));

    // Redstone Powered Gem — crafting component; the headtorch's lamp "bulb".
    public static final DeferredItem<Item> REDSTONE_POWERED_GEM = ITEMS.register("redstone_powered_gem",
            () -> new Item(new Item.Properties()));

    // Redstone Battery — portable, reusable charge storage that powers redstone gadgets.
    public static final DeferredItem<Item> REDSTONE_BATTERY = ITEMS.register("redstone_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1)));

    // Redstone Miner Headtorch — casts a light spot + red beam where the wearer looks.
    public static final DeferredItem<Item> REDSTONE_MINER_HEADTORCH = ITEMS.register("redstone_miner_headtorch",
            () -> new HeadtorchItem(new Item.Properties().stacksTo(1)));

    // Redstone Rocket Boots — mid-air rocket jump + elytra thrust, battery-powered.
    public static final DeferredItem<Item> REDSTONE_ROCKET_BOOTS = ITEMS.register("redstone_rocket_boots",
            () -> new RocketBootsItem(new Item.Properties().stacksTo(1)));

    public RedstoneLapisMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the deferred registers to the mod event bus.
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);

        // Place our items into a vanilla creative tab.
        modEventBus.addListener(this::addCreative);

        // Grant night vision while the goggles are worn (game/server event bus).
        NeoForge.EVENT_BUS.addListener(GogglesHandler::onPlayerTick);

        // Project the headtorch light spot; clean it up when players leave.
        NeoForge.EVENT_BUS.addListener(HeadtorchHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(HeadtorchHandler::onPlayerLogout);

        // Rocket boots: thrust ticking, fall-damage grace, state cleanup.
        // (The jump/thrust payloads arrive via network/ModNetworking.)
        NeoForge.EVENT_BUS.addListener(RocketBootsHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RocketBootsHandler::onLivingFall);
        NeoForge.EVENT_BUS.addListener(RocketBootsHandler::onPlayerLogout);

        LOGGER.info("Redstone & Lapis Mod loaded");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(REDSTONE_GOGGLES);
            event.accept(REDSTONE_MINER_HEADTORCH);
            event.accept(REDSTONE_ROCKET_BOOTS);
            event.accept(REDSTONE_BATTERY);                                  // empty battery
            event.accept(BatteryItem.fullyCharged(REDSTONE_BATTERY.get()));  // pre-charged, for testing
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(REDSTONE_GOGGLE_LENS);
            event.accept(REDSTONE_POWERED_GEM);
        }
    }
}
