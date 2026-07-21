package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Battery-powered rocket boots. While worn and powered: press jump again in
 * mid-air for a ~10-block rocket jump ({@link RocketBootsHandler} bills it and
 * forgives the landing), and hold jump while elytra-gliding for continuous
 * rocket thrust stronger than a firework. All socket/charge plumbing comes
 * from {@link PoweredGearItem}; the input detection lives client-side in
 * RocketBootsClientHandler.
 */
public class RocketBootsItem extends PoweredGearItem {

    public RocketBootsItem(Properties properties) {
        super(RedstoneLapisMod.ROCKET_BOOTS_ARMOR_MATERIAL, ArmorItem.Type.BOOTS, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.rocket_boots.jump")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.rocket_boots.thrust")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag); // battery status lines
    }
}
