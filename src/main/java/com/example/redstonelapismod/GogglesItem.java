package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.vision")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.scan")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
