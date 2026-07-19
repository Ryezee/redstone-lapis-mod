package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Battery-powered miner headtorch. While worn and powered,
 * {@link HeadtorchHandler} keeps an invisible vanilla light block at the spot
 * the player is looking at (real directional lighting response), and the client
 * draws a red dust-mote beam cone along the gaze. All socket/charge plumbing
 * comes from {@link PoweredHeadgearItem}.
 */
public class HeadtorchItem extends PoweredHeadgearItem {

    public HeadtorchItem(Properties properties) {
        super(RedstoneLapisMod.HEADTORCH_ARMOR_MATERIAL, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.headtorch.beam")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag); // battery status lines
    }
}
