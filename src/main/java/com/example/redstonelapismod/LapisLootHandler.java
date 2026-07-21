package com.example.redstonelapismod;

import java.util.Set;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Injects Concentrated Lapis Lazuli into underground structure chest loot —
 * the ONLY source of lapis-gadget fuel (deliberately not craftable).
 *
 * LootTableLoadEvent fires once per loot table as the server loads its data;
 * we append our own pool to the underground chest tables. A pool is one
 * independent drawing: ours rolls once per chest with a 25% chance of a
 * single cell, so we never touch or reweight the table's existing loot —
 * safe alongside whatever the pack's other 156 mods do to the same tables.
 */
public final class LapisLootHandler {

    /** Chance per chest of containing one Concentrated Lapis Lazuli. */
    private static final float DROP_CHANCE = 0.25F;

    /** Underground structure chests only — lapis is a treasure of the deep. */
    private static final Set<ResourceKey<LootTable>> TARGET_TABLES = Set.of(
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.STRONGHOLD_CROSSING,
            BuiltInLootTables.ANCIENT_CITY);

    private LapisLootHandler() {}

    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TARGET_TABLES.contains(event.getKey())) {
            return;
        }
        event.getTable().addPool(LootPool.lootPool()
                .name(RedstoneLapisMod.MODID + ":concentrated_lapis")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(DROP_CHANCE))
                .add(LootItem.lootTableItem(RedstoneLapisMod.CONCENTRATED_LAPIS_LAZULI.get()))
                .build());
    }
}
