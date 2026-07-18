package com.example.redstonelapismod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Grants sight in the dark while the Redstone Goggles are worn.
 *
 * Rather than reimplement lighting, we lean on vanilla's Night Vision, which
 * brightens the lightmap to full — so it cleanly cuts through dark caves and
 * nights. We (re)apply a short effect every tick while the goggles are on the
 * head, and clear it the moment they come off.
 *
 * Applied on the logical server so the effect syncs to the client normally;
 * this works in singleplayer (integrated server) and on servers running this mod.
 */
public final class GogglesHandler {
    // Ticks. Kept above 200 so Night Vision renders steady (below that it flickers).
    private static final int EFFECT_DURATION = 220;

    private GogglesHandler() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return; // server-side only, so the effect syncs to the client
        }

        boolean wearing = player.getItemBySlot(EquipmentSlot.HEAD).is(RedstoneLapisMod.REDSTONE_GOGGLES.get());
        if (wearing) {
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
