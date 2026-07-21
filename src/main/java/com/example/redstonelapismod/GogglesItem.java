package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Battery-powered head goggles. The socket/charge-bar/battery-tooltip plumbing
 * lives in {@link PoweredGearItem}; the "goggles" ArmorMaterial in
 * {@link RedstoneLapisMod} makes vanilla's armor renderer draw
 * textures/models/armor/goggles_layer_1.png fitted to the head.
 * While worn and powered, {@link GogglesHandler} grants vision in the dark and
 * the client sprinkles ore sparkles + the visor overlay.
 */
public class GogglesItem extends PoweredGearItem {

    public GogglesItem(Properties properties) {
        super(RedstoneLapisMod.GOGGLES_ARMOR_MATERIAL, ArmorItem.Type.HELMET, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.vision")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_goggles.scan")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag); // battery status lines
    }
}
