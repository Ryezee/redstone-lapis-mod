package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A portable, reusable store of redstone charge. Gadgets pull power from it via
 * {@link #tryDrain(Player, int)} — they never talk to the data component directly,
 * so if we ever change how charge is stored, only this class changes.
 */
public class BatteryItem extends Item {

    /** Full capacity of one battery, in charge units. */
    public static final int MAX_CHARGE = 1000;

    /** Charge gained per redstone dust consumed when recharging (full battery = 40 dust). */
    public static final int CHARGE_PER_DUST = 25;

    /** Redstone-red charge bar. */
    private static final int BAR_COLOR = 0xE83232;

    public BatteryItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Charge API — the surface other gadgets use.
    // ------------------------------------------------------------------

    /** Reads the charge stored on this stack; a battery with no component is empty. */
    public static int getCharge(ItemStack stack) {
        return stack.getOrDefault(RedstoneLapisMod.CHARGE.get(), 0);
    }

    /** Writes the charge on this stack, clamped to [0, MAX_CHARGE]. */
    public static void setCharge(ItemStack stack, int amount) {
        stack.set(RedstoneLapisMod.CHARGE.get(), Mth.clamp(amount, 0, MAX_CHARGE));
    }

    /**
     * Drains {@code amount} from the first battery in the player's inventory that
     * holds enough charge. Returns true if power was supplied.
     */
    public static boolean tryDrain(Player player, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BatteryItem && getCharge(stack) >= amount) {
                setCharge(stack, getCharge(stack) - amount);
                return true;
            }
        }
        return false;
    }

    /**
     * True if any battery in the player's inventory holds at least {@code amount}.
     * A read-only peek — gadgets use this to decide "am I powered?" without spending charge.
     */
    public static boolean hasCharge(Player player, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BatteryItem && getCharge(stack) >= amount) {
                return true;
            }
        }
        return false;
    }

    /** A fully charged battery stack, used for the creative tab entry. */
    public static ItemStack fullyCharged(Item item) {
        ItemStack stack = new ItemStack(item);
        setCharge(stack, MAX_CHARGE);
        return stack;
    }

    // ------------------------------------------------------------------
    // Recharging — right-click eats redstone dust from the inventory.
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack battery = player.getItemInHand(hand);
        int missing = MAX_CHARGE - getCharge(battery);
        if (missing <= 0) {
            return InteractionResultHolder.pass(battery); // already full — do nothing
        }

        // Mutate inventory on the logical server only; the result syncs to the client.
        if (!level.isClientSide) {
            int dustNeeded = (missing + CHARGE_PER_DUST - 1) / CHARGE_PER_DUST; // ceil division
            int consumed = 0;
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < inv.getContainerSize() && consumed < dustNeeded; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack.is(Items.REDSTONE)) {
                    int take = Math.min(stack.getCount(), dustNeeded - consumed);
                    stack.shrink(take);
                    consumed += take;
                }
            }

            if (consumed > 0) {
                setCharge(battery, getCharge(battery) + consumed * CHARGE_PER_DUST);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.8F, 0.6F);
            } else {
                // true = action bar (above the hotbar), not the chat log
                player.displayClientMessage(
                        Component.translatable("message.redstonelapismod.battery.no_redstone"), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(battery, level.isClientSide);
    }

    // ------------------------------------------------------------------
    // Display — the durability-style bar shows remaining charge.
    // ------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.battery.charge",
                getCharge(stack), MAX_CHARGE).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.battery.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
