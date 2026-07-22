package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Redstone Rocket Launcher — a dumb tube, on purpose: it fires whatever
 * {@link RocketAmmoItem} it finds and asks THAT item for speed, sound,
 * cooldown, and payload. Ammo priority: the offhand first (hold the round
 * you want to fire), then the first stack anywhere in the inventory.
 *
 * Works from either hand for free, same as the Echo Lens: vanilla routes
 * right-clicks through {@code use()} main hand first, then offhand.
 */
public class RedstoneRocketLauncherItem extends Item {

    public RedstoneRocketLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;

        ItemStack ammoStack = findAmmo(player);
        if (ammoStack.isEmpty() && creative) {
            // Creative with no ammo on hand: conjure a standard rocket.
            ammoStack = new ItemStack(RedstoneLapisMod.REDSTONE_ROCKET.get());
        }
        if (ammoStack.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.redstonelapismod.rocket_launcher.no_ammo"), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            return InteractionResultHolder.fail(launcher);
        }

        RocketAmmoItem ammo = (RocketAmmoItem) ammoStack.getItem();

        if (!level.isClientSide) {
            RedstoneRocketEntity rocket = new RedstoneRocketEntity(level, player);
            rocket.setItem(ammoStack); // which type flies — synced to all clients
            // Aim along the player's current view (pitch, yaw); roll offset 0.
            rocket.shootFromRotation(player, player.getXRot(), player.getYRot(),
                    0.0F, ammo.speed(), ammo.inaccuracy());
            level.addFreshEntity(rocket);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ammo.fireSound(), SoundSource.PLAYERS, 1.0F, ammo.firePitch());

            if (!creative) {
                ammoStack.shrink(1);
            }
        }

        player.getCooldowns().addCooldown(this, ammo.cooldownTicks());
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(launcher, level.isClientSide);
    }

    /**
     * Ammo selection: offhand wins (deliberate choice of round), otherwise
     * the first RocketAmmoItem anywhere in the inventory (hotbar, main,
     * armor, offhand — getContainerSize spans all compartments).
     */
    private static ItemStack findAmmo(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RocketAmmoItem) {
            return offhand;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof RocketAmmoItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.rocket_launcher.fire")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.rocket_launcher.blast")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
