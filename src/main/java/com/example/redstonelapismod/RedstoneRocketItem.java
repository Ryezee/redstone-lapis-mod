package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Redstone Rocket — ammunition for the Redstone Rocket Launcher. Inert on its
 * own (no right-click behavior); the launcher finds and consumes these from
 * the shooter's inventory. Its 16x16 texture doubles as the in-flight sprite
 * via {@link RedstoneRocketEntity#getDefaultItem}.
 */
public class RedstoneRocketItem extends Item {

    public RedstoneRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_rocket.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
