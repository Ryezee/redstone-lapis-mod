package com.example.redstonelapismod.client;

import com.example.redstonelapismod.BatteryItem;
import com.example.redstonelapismod.GogglesItem;
import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

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
     * The client can check this locally because equipment and inventories (and the
     * charge component on each stack) are synced from the server automatically.
     */
    public static boolean isPowered(Player player) {
        if (!isWorn(player)) {
            return false;
        }
        return GogglesItem.installedCharge(player.getItemBySlot(EquipmentSlot.HEAD)) > 0
                || BatteryItem.hasCharge(player, 1);
    }
}
