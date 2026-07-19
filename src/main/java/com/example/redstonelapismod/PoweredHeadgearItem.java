package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Base class for battery-powered head gadgets (goggles, headtorch, ...).
 * Owns the battery socket: a battery is the charge it carries and nothing else,
 * so "installing" one just moves its charge number onto the gear's stack (the
 * same CHARGE component) and consumes the item; "ejecting" mints a battery
 * carrying that number back out. Also owns the charge bar and battery tooltip.
 */
public class PoweredHeadgearItem extends ArmorItem {

    public PoweredHeadgearItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, ArmorItem.Type.HELMET, properties);
    }

    // ------------------------------------------------------------------
    // Socket state.
    // ------------------------------------------------------------------

    /** True when a battery is installed (even a dead one — component present, maybe 0). */
    public static boolean hasBatteryInstalled(ItemStack gear) {
        return gear.has(RedstoneLapisMod.CHARGE.get());
    }

    /** Charge of the installed battery; 0 when empty OR when nothing is installed. */
    public static int installedCharge(ItemStack gear) {
        return gear.getOrDefault(RedstoneLapisMod.CHARGE.get(), 0);
    }

    // ------------------------------------------------------------------
    // Power API used by the server-side handlers.
    // ------------------------------------------------------------------

    /** True when the socketed battery (first) or any loose inventory battery can supply {@code amount}. */
    public static boolean isPowered(Player player, ItemStack gear, int amount) {
        return installedCharge(gear) >= amount || BatteryItem.hasCharge(player, amount);
    }

    /** Drains {@code amount}: socketed battery first, loose inventory batteries as fallback. */
    public static void drainOnePowerTick(Player player, ItemStack gear, int amount) {
        int installed = installedCharge(gear);
        if (installed >= amount) {
            gear.set(RedstoneLapisMod.CHARGE.get(), installed - amount);
        } else {
            BatteryItem.tryDrain(player, amount);
        }
    }

    // ------------------------------------------------------------------
    // Install / eject: bundle-style inventory clicks.
    // ------------------------------------------------------------------

    /**
     * Called when another stack, held on the cursor, is right-clicked onto this
     * gear in an inventory slot. Battery on cursor -> install it. Empty cursor
     * -> eject the installed battery.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack gear, ItemStack other, Slot slot,
            ClickAction action, Player player, SlotAccess cursorAccess) {
        if (action != ClickAction.SECONDARY) {
            return false; // only right-click; left-click keeps vanilla behavior
        }

        // Install: battery on the cursor, no battery in the gear yet.
        if (other.getItem() instanceof BatteryItem && !hasBatteryInstalled(gear)) {
            gear.set(RedstoneLapisMod.CHARGE.get(), BatteryItem.getCharge(other));
            other.shrink(1); // batteries don't stack, so this empties the cursor
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.9F);
            return true; // handled — suppress the default click
        }

        // Eject: empty cursor, battery installed — pop it back out onto the cursor.
        if (other.isEmpty() && hasBatteryInstalled(gear)) {
            ItemStack battery = new ItemStack(RedstoneLapisMod.REDSTONE_BATTERY.get());
            BatteryItem.setCharge(battery, installedCharge(gear));
            gear.remove(RedstoneLapisMod.CHARGE.get());
            cursorAccess.set(battery);
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.9F);
            return true;
        }

        return false;
    }

    // ------------------------------------------------------------------
    // Display — charge bar for the installed battery, like BatteryItem's.
    // ------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return hasBatteryInstalled(stack); // no bar at all until a battery is socketed
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * installedCharge(stack) / BatteryItem.MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE83232;
    }

    /**
     * Battery status lines. Subclasses add their own gadget lines first,
     * then call {@code super.appendHoverText(...)} to append these.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        if (hasBatteryInstalled(stack)) {
            tooltip.add(Component.translatable("tooltip.redstonelapismod.headgear.battery",
                    installedCharge(stack), BatteryItem.MAX_CHARGE).withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.redstonelapismod.headgear.power")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.redstonelapismod.headgear.insert_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
