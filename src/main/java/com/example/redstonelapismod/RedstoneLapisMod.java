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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
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

    // Deferred register for this mod's blocks — first customer: the lapis lightning rod.
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    // Lapis Lightning Rod — copper-rod behavior, but it summons its own strikes
    // in storms and pays out XP. randomTicks() drives the storm logic.
    public static final DeferredBlock<LapisLightningRodBlock> LAPIS_LIGHTNING_ROD =
            BLOCKS.register("lapis_lightning_rod", () -> new LapisLightningRodBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.LAPIS)
                            .forceSolidOn()
                            .requiresCorrectToolForDrops()
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.COPPER)
                            .noOcclusion()
                            .randomTicks()));

    // Deferred register for this mod's data component types (typed per-ItemStack data, 1.20.5+).
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    // "charge" — how much redstone power an item currently stores (used by the battery).
    // persistent(...) = how it's written to disk; networkSynchronized(...) = how it's sent to clients.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE =
            DATA_COMPONENTS.registerComponentType("charge", builder -> builder
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    // Deferred register for this mod's entity types (things that exist freely in
    // the world — not on the block grid). First customer: the rocket projectile.
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    // The Redstone Rocket in flight. Snowball-family settings: a 0.25-block cube
    // hitbox, tracked by clients within 4 chunks, position resync every 10 ticks
    // (clients predict movement between resyncs).
    public static final DeferredHolder<EntityType<?>, EntityType<RedstoneRocketEntity>> REDSTONE_ROCKET_ENTITY =
            ENTITY_TYPES.register("redstone_rocket", () -> EntityType.Builder
                    .<RedstoneRocketEntity>of(RedstoneRocketEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("redstone_rocket"));

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

    // Redstone Rocket — ammo for the launcher; also the in-flight sprite.
    public static final DeferredItem<Item> REDSTONE_ROCKET = ITEMS.register("redstone_rocket",
            () -> new RedstoneRocketItem(new Item.Properties().stacksTo(16)));

    // Mega Redstone Rocket — TNT-plus blast with real block damage.
    public static final DeferredItem<Item> MEGA_REDSTONE_ROCKET = ITEMS.register("mega_redstone_rocket",
            () -> new MegaRedstoneRocketItem(new Item.Properties().stacksTo(16)));

    // Redstone Nuclear Warhead — crafting component for the Mega Nuke Rocket.
    public static final DeferredItem<Item> REDSTONE_NUCLEAR_WARHEAD = ITEMS.register("redstone_nuclear_warhead",
            () -> new Item(new Item.Properties().stacksTo(16)));

    // Redstone Mega Nuke Rocket — warhead-tipped shell: crater blast + mushroom cloud.
    public static final DeferredItem<Item> MEGA_NUKE_ROCKET = ITEMS.register("mega_nuke_rocket",
            () -> new MegaNukeRocketItem(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON)));

    // Lapis Lazuli Laser — railgun round: straight beam, transmutes blocks to lapis.
    // Rarity.RARE aqua name, matching its Concentrated Lapis pedigree.
    public static final DeferredItem<Item> LAPIS_LAZULI_LASER = ITEMS.register("lapis_lazuli_laser",
            () -> new LapisLaserItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    // Redstone Rocket Launcher — dumb tube; damage and effects come from the ammo.
    public static final DeferredItem<Item> REDSTONE_ROCKET_LAUNCHER = ITEMS.register("redstone_rocket_launcher",
            () -> new RedstoneRocketLauncherItem(new Item.Properties().stacksTo(1)));

    // --- The lapis family: exclusive, expensive, loot-fueled. ---

    // Lapis Powered Gem — crafting component; heart of every lapis gadget.
    public static final DeferredItem<Item> LAPIS_POWERED_GEM = ITEMS.register("lapis_powered_gem",
            () -> new Item(new Item.Properties()));

    // Concentrated Lapis Lazuli — loot-only fuel cell for lapis gadget tanks.
    // Rarity.RARE renders the name in aqua — the "this is special" signal.
    public static final DeferredItem<Item> CONCENTRATED_LAPIS_LAZULI = ITEMS.register("concentrated_lapis_lazuli",
            () -> new ConcentratedLapisItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    // Lapis Lazuli Ingot — smelt a lapis block; the lapis family's crafting metal.
    public static final DeferredItem<Item> LAPIS_LAZULI_INGOT = ITEMS.register("lapis_lazuli_ingot",
            () -> new Item(new Item.Properties()));

    // The rod's item form (what sits in inventory and gets placed).
    public static final DeferredItem<Item> LAPIS_LIGHTNING_ROD_ITEM = ITEMS.register("lapis_lightning_rod",
            () -> new BlockItem(LAPIS_LIGHTNING_ROD.get(), new Item.Properties()));

    // Lapis Paragon — legendary loot-only gem: lifts maxed enchantments past their cap.
    public static final DeferredItem<Item> LAPIS_PARAGON = ITEMS.register("lapis_paragon",
            () -> new LapisParagonItem(new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)));

    // Lapis Echo Lens — handheld sonar: pulse reveals nearby creatures through walls.
    public static final DeferredItem<Item> LAPIS_ECHO_LENS = ITEMS.register("lapis_echo_lens",
            () -> new EchoLensItem(new Item.Properties().stacksTo(1)));

    public RedstoneLapisMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the deferred registers to the mod event bus.
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        // Place our items into a vanilla creative tab.
        modEventBus.addListener(this::addCreative);

        // Grant night vision while the goggles are worn (game/server event bus).
        NeoForge.EVENT_BUS.addListener(GogglesHandler::onPlayerTick);

        // Project the headtorch light spot; clean it up when players leave.
        NeoForge.EVENT_BUS.addListener(HeadtorchHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(HeadtorchHandler::onPlayerLogout);

        // Rocket boots: fall-damage grace + state cleanup.
        // (The charged-blast payload arrives via network/ModNetworking.)
        NeoForge.EVENT_BUS.addListener(RocketBootsHandler::onLivingFall);
        NeoForge.EVENT_BUS.addListener(RocketBootsHandler::onPlayerLogout);

        // Echo Lens: tick the expanding sonar pulses; drop them when the server dies.
        NeoForge.EVENT_BUS.addListener(EchoPulseHandler::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(EchoPulseHandler::onServerStopped);

        // Concentrated Lapis Lazuli spawns in underground structure chests.
        NeoForge.EVENT_BUS.addListener(LapisLootHandler::onLootTableLoad);

        LOGGER.info("Redstone & Lapis Mod loaded");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(REDSTONE_GOGGLES);
            event.accept(REDSTONE_MINER_HEADTORCH);
            event.accept(REDSTONE_ROCKET_BOOTS);
            event.accept(REDSTONE_BATTERY);                                  // empty battery
            event.accept(BatteryItem.fullyCharged(REDSTONE_BATTERY.get()));  // pre-charged, for testing
            event.accept(LAPIS_ECHO_LENS);                                   // empty tank
            event.accept(LapisGadgetItem.fullyFueled(LAPIS_ECHO_LENS.get())); // full tank, for testing
            event.accept(CONCENTRATED_LAPIS_LAZULI);
            event.accept(LAPIS_PARAGON);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(REDSTONE_ROCKET_LAUNCHER);
            event.accept(REDSTONE_ROCKET);
            event.accept(MEGA_REDSTONE_ROCKET);
            event.accept(MEGA_NUKE_ROCKET);
            event.accept(LAPIS_LAZULI_LASER);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(REDSTONE_GOGGLE_LENS);
            event.accept(REDSTONE_POWERED_GEM);
            event.accept(REDSTONE_NUCLEAR_WARHEAD);
            event.accept(LAPIS_POWERED_GEM);
            event.accept(LAPIS_LAZULI_INGOT);
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(LAPIS_LIGHTNING_ROD_ITEM);
        }
    }
}
