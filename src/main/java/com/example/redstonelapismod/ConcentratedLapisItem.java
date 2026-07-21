package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Concentrated Lapis Lazuli — the loot-only fuel cell of the lapis power
 * system. Not craftable: found in underground structure chests (see
 * LapisLootHandler), which keeps lapis gadgets exclusive by design.
 *
 * The cell itself is inert; right-click it onto a {@link LapisGadgetItem}
 * to consume it and add {@link #CHARGE_PER_CELL} to the gadget's tank
 * (that absorb logic lives on the gadget — the thing being clicked ON).
 */
public class ConcentratedLapisItem extends Item {

    /** Lapis charge one cell feeds into a gadget tank (10 Echo Lens scans). */
    public static final int CHARGE_PER_CELL = 100;

    public ConcentratedLapisItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.concentrated_lapis.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.concentrated_lapis.fuel",
                CHARGE_PER_CELL).withStyle(ChatFormatting.BLUE));
    }
}
