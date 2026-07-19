package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A simple head-slot wearable. Implementing {@link Equipable} (rather than
 * extending ArmorItem) makes it equip to the head on right-click without
 * needing an armor material or a worn-armor texture — the "basic form".
 * It renders nothing on the head when worn; its only job is to sit in the
 * head slot so {@link GogglesHandler} can grant vision in the dark.
 */
public class GogglesItem extends Item implements Equipable {
    public GogglesItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Right-clicking swaps the goggles into the head slot (and back out).
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    // ------------------------------------------------------------------
    // Battery socket. A battery is the charge it carries and nothing else,
    // so "installing" one just moves its charge number onto the goggles
    // stack (same CHARGE component) and consumes the item; "ejecting"
    // mints a battery carrying that number back out.
    // ------------------------------------------------------------------

    /** True when a battery is installed (even a dead one — component present, maybe 0). */
    public static boolean hasBatteryInstalled(ItemStack goggles) {
        return goggles.has(RedstoneLapisMod.CHARGE.get());
    }

    /** Charge of the installed battery; 0 when empty OR when nothing is installed. */
    public static int installedCharge(ItemStack goggles) {
        return goggles.getOrDefault(RedstoneLapisMod.CHARGE.get(), 0);
    }

    /**
     * Bundle-style inventory interaction: called when another stack, held on the
     * cursor, is right-clicked onto the goggles in an inventory slot.
     * Battery on cursor -> install it. Empty cursor -> eject the installed battery.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack goggles, ItemStack other, Slot slot,
            ClickAction action, Player player, SlotAccess cursorAccess) {
        if (action != ClickAction.SECONDARY) {
            return false; // only right-click; left-click keeps vanilla behavior
        }

        // Install: battery on the cursor, no battery in the goggles yet.
        if (other.getItem() instanceof BatteryItem && !hasBatteryInstalled(goggles)) {
            goggles.set(RedstoneLapisMod.CHARGE.get(), BatteryItem.getCharge(other));
            other.shrink(1); // batteries don't stack, so this empties the cursor
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.9F);
            return true; // handled — suppress the default click
        }

        // Eject: empty cursor, battery installed — pop it back out onto the cursor.
        if (other.isEmpty() && hasBatteryInstalled(goggles)) {
            ItemStack battery = new ItemStack(RedstoneLapisMod.REDSTONE_BATTERY.get());
            BatteryItem.setCharge(battery, installedCharge(goggles));
            goggles.remove(RedstoneLapisMod.CHARGE.get());
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.vision")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.scan")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (hasBatteryInstalled(stack)) {
            tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.battery",
                    installedCharge(stack), BatteryItem.MAX_CHARGE).withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.power")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.insert_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
