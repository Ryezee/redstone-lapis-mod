package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The standard Redstone Rocket — the launcher's baseline ammo. All of its
 * behavior (flat flight, concussion blast, red trail) IS the default set in
 * {@link RocketAmmoItem}; this subclass only contributes its tooltip. Its
 * 16x16 texture doubles as the in-flight sprite.
 */
public class RedstoneRocketItem extends RocketAmmoItem {

    public RedstoneRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.redstone_rocket.blast")
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
