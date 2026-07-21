package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Lapis Echo Lens — handheld sonar. Right-click to spend
 * {@link #SCAN_COST} lapis charge and emit an expanding pulse
 * ({@link EchoPulseHandler}) that reveals every creature it washes over
 * within {@link #SCAN_RADIUS} blocks with the Glowing outline.
 *
 * Works from either hand: vanilla's right-click routing tries the main hand
 * first, then the offhand, and hands us whichever fired via the
 * InteractionHand parameter of {@link #use} — so offhand support costs
 * nothing extra. (Caveat inherited from vanilla: a main-hand item with its
 * own right-click action, like food or blocks, eats the click first.)
 */
public class EchoLensItem extends LapisGadgetItem {

    /** Lapis charge per scan (user spec). */
    public static final int SCAN_COST = 10;
    /** How far the pulse reaches, in blocks (user spec). */
    public static final double SCAN_RADIUS = 10.0;
    /** Re-ping cooldown, in ticks (3 s) — vanilla blocks use() while it runs. */
    public static final int COOLDOWN_TICKS = 60;

    public EchoLensItem(Properties properties) {
        super(properties);
    }

    /**
     * [MC hook] Vanilla calls this on right-click with the lens in the firing
     * hand. Runs on BOTH logical sides: the client result drives the arm swing
     * instantly; the server does the real work (drain, pulse, cooldown).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack lens = player.getItemInHand(hand);

        // The CHARGE component syncs to clients, so both sides agree on this check.
        if (getCharge(lens) < SCAN_COST) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.redstonelapismod.echo_lens.no_charge"), true);
            }
            return InteractionResultHolder.fail(lens);
        }

        if (level instanceof ServerLevel serverLevel) {
            tryDrain(lens, SCAN_COST);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            // Deep resonant cast ping, audible to everyone nearby.
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            EchoPulseHandler.startPulse(serverLevel, player.getEyePosition());
        }

        return InteractionResultHolder.sidedSuccess(lens, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.echo_lens.scan")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.echo_lens.cost",
                SCAN_COST, (int) SCAN_RADIUS).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag); // tank status lines
    }
}
