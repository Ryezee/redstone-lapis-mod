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
 * Redstone Rocket Launcher — a dumb tube: the rocket is the entire cost (no
 * battery drain, user design). Right-click fires the first Redstone Rocket
 * found anywhere in the inventory as a {@link RedstoneRocketEntity}; the
 * blast is concussion-only (no block damage).
 *
 * Works from either hand for free, same as the Echo Lens: vanilla routes
 * right-clicks through {@code use()} main hand first, then offhand.
 */
public class RedstoneRocketLauncherItem extends Item {

    /** Time between shots (ticks) — vanilla ItemCooldowns gates use() and draws the sweep. */
    public static final int COOLDOWN_TICKS = 30;
    /** Muzzle velocity in blocks/tick (arrow at full draw is 3.0). */
    public static final float ROCKET_SPEED = 3.0F;
    /** Spread — 0 is laser-perfect; snowballs use 1.0. Slight wobble, still a rifle. */
    public static final float INACCURACY = 0.25F;

    public RedstoneRocketLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;

        ItemStack ammo = findRocket(player);
        if (ammo.isEmpty() && !creative) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.redstonelapismod.rocket_launcher.no_ammo"), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            return InteractionResultHolder.fail(launcher);
        }

        if (!level.isClientSide) {
            RedstoneRocketEntity rocket = new RedstoneRocketEntity(level, player);
            // Aim along the player's current view (pitch, yaw); roll offset 0.
            rocket.shootFromRotation(player, player.getXRot(), player.getYRot(),
                    0.0F, ROCKET_SPEED, INACCURACY);
            level.addFreshEntity(rocket);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.7F);

            if (!creative) {
                ammo.shrink(1);
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(launcher, level.isClientSide);
    }

    /**
     * First Redstone Rocket stack anywhere in the inventory (hotbar, main,
     * armor, offhand — getContainerSize spans all compartments), or EMPTY.
     */
    private static ItemStack findRocket(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(RedstoneLapisMod.REDSTONE_ROCKET.get())) {
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
                .withStyle(ChatFormatting.RED));
    }
}
