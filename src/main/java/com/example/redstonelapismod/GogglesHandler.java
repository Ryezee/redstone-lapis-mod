package com.example.redstonelapismod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Grants sight in the dark while the Redstone Goggles are worn.
 *
 * Rather than reimplement lighting, we lean on vanilla's Night Vision, which
 * brightens the lightmap to full — so it cleanly cuts through dark caves and
 * nights. We (re)apply a short effect every tick while the goggles are on the
 * head AND powered by a Redstone Battery in the inventory (1 charge/sec), and
 * clear it the moment they come off or the battery dies.
 *
 * Applied on the logical server so the effect syncs to the client normally;
 * this works in singleplayer (integrated server) and on servers running this mod.
 */
public final class GogglesHandler {
    // Ticks. Kept above 200 so Night Vision renders steady (below that it flickers).
    private static final int EFFECT_DURATION = 220;

    // Charge drained from a battery per second of wearing the goggles (full battery ~16 min).
    private static final int DRAIN_PER_SECOND = 1;

    private GogglesHandler() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return; // server-side only, so the effect syncs to the client
        }

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        // Powered = wearing AND (socketed battery first, loose inventory battery as fallback).
        boolean powered = head.is(RedstoneLapisMod.REDSTONE_GOGGLES.get())
                && PoweredHeadgearItem.isPowered(player, head, DRAIN_PER_SECOND);

        if (powered) {
            // Bill once a second, not every tick (20 ticks = 1 second).
            if (player.tickCount % 20 == 0) {
                PoweredHeadgearItem.drainOnePowerTick(player, head, DRAIN_PER_SECOND);
            }
            // ambient=false, visible=false, showIcon=false -> no particles, no HUD icon.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, false, false, false));
        } else {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            // Only clear OUR short-lived effect — never a real night-vision potion,
            // which always has a far longer remaining duration.
            if (current != null && current.getDuration() <= EFFECT_DURATION) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }
}
