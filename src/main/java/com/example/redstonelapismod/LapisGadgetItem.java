package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Base class for LAPIS-powered handheld gadgets (Echo Lens, future wards...).
 *
 * The lapis power system is deliberately different from redstone's: there is
 * no removable battery. Each gadget has an INTERNAL TANK (capacity
 * {@link #TANK_CAPACITY}) that you fill by right-clicking a Concentrated
 * Lapis Lazuli onto the gadget in your inventory — the cell is CONSUMED and
 * its charge absorbed, one-way. Fuel cells are loot-only, so lapis gadgets
 * stay exclusive and expensive by design.
 *
 * Charge is stored in the same CHARGE data component the redstone family
 * uses — the component is just a typed "int on this stack" key; what the
 * number MEANS (lapis vs redstone) is decided by the item that carries it.
 */
public class LapisGadgetItem extends Item {

    /** Max lapis charge a gadget tank holds (5 Concentrated Lapis worth). */
    public static final int TANK_CAPACITY = 500;

    /** Lapis-blue charge bar (matches the HUD lapis bar). */
    private static final int BAR_COLOR = 0x3C6CE8;

    public LapisGadgetItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Tank API — the surface gadget behaviors use.
    // ------------------------------------------------------------------

    /** Lapis charge currently in this gadget's tank. */
    public static int getCharge(ItemStack gadget) {
        return gadget.getOrDefault(RedstoneLapisMod.CHARGE.get(), 0);
    }

    /** Writes the tank, clamped to [0, TANK_CAPACITY]. */
    public static void setCharge(ItemStack gadget, int amount) {
        gadget.set(RedstoneLapisMod.CHARGE.get(), Mth.clamp(amount, 0, TANK_CAPACITY));
    }

    /** Spends {@code amount} from the tank. Returns false (and spends nothing) if short. */
    public static boolean tryDrain(ItemStack gadget, int amount) {
        int charge = getCharge(gadget);
        if (charge < amount) {
            return false;
        }
        setCharge(gadget, charge - amount);
        return true;
    }

    /** A gadget with a full tank, for the creative tab. */
    public static ItemStack fullyFueled(Item item) {
        ItemStack stack = new ItemStack(item);
        setCharge(stack, TANK_CAPACITY);
        return stack;
    }

    // ------------------------------------------------------------------
    // Fueling: right-click a Concentrated Lapis Lazuli onto the gadget.
    // ------------------------------------------------------------------

    /**
     * Same inventory gesture as installing a redstone battery, but one-way:
     * the cell is consumed into the tank. Refuses (rather than wastes) a cell
     * that would overflow the tank.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack gadget, ItemStack other, Slot slot,
            ClickAction action, Player player, SlotAccess cursorAccess) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof ConcentratedLapisItem)) {
            return false; // only right-click with a fuel cell; everything else stays vanilla
        }

        if (getCharge(gadget) + ConcentratedLapisItem.CHARGE_PER_CELL > TANK_CAPACITY) {
            // Not enough room for the WHOLE cell — refuse so none of it is wasted.
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
            return true; // still handled: don't let vanilla swap the stacks
        }

        setCharge(gadget, getCharge(gadget) + ConcentratedLapisItem.CHARGE_PER_CELL);
        other.shrink(1);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
        return true;
    }

    // ------------------------------------------------------------------
    // Display — blue fuel gauge + tank tooltip.
    // ------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true; // a fuel gauge should read "empty" loudly, not hide
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / TANK_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    /**
     * Tank status lines. Subclasses add their gadget lines first, then call
     * {@code super.appendHoverText(...)} — same convention as PoweredGearItem.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.lapis_gadget.charge",
                getCharge(stack), TANK_CAPACITY).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.lapis_gadget.feed_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
