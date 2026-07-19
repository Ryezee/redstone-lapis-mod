package com.example.redstonelapismod.client;

import com.example.redstonelapismod.BatteryItem;
import com.example.redstonelapismod.PoweredHeadgearItem;
import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Small client-side helpers shared by the goggles' visual features
 * (the passive ore sparkle and the visor overlay).
 *
 * <p>Both features need to answer the same question — "are the goggles on the head?" — so that check
 * lives here in one place instead of being copy-pasted. A {@code private} constructor keeps anyone
 * from instantiating what is really just a bag of static utilities.
 */
public final class GogglesClient {
    private GogglesClient() {}

    /** True when {@code player} exists and is wearing the Redstone Goggles in the head slot. */
    public static boolean isWorn(Player player) {
        return player != null
                && player.getItemBySlot(EquipmentSlot.HEAD).is(RedstoneLapisMod.REDSTONE_GOGGLES.get());
    }

    /**
     * True when the goggles are worn AND powered — first by the battery socketed
     * into the goggles, falling back to any loose battery in the inventory.
     */
    public static boolean isPowered(Player player) {
        return isWearingPowered(player, RedstoneLapisMod.REDSTONE_GOGGLES.get());
    }

    /**
     * Generic form for any powered headgear (goggles, headtorch, ...): worn in the
     * head slot AND the socketed battery (first) or a loose inventory battery has
     * charge. The client can check this locally because equipment and inventories
     * (and the charge component on each stack) are synced from the server.
     */
    public static boolean isWearingPowered(Player player, Item item) {
        if (player == null) {
            return false;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(item)) {
            return false;
        }
        return PoweredHeadgearItem.installedCharge(head) > 0 || BatteryItem.hasCharge(player, 1);
    }
}
