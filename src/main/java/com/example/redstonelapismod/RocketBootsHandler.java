package com.example.redstonelapismod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server half of the Redstone Rocket Boots' charged blast.
 *
 * The CLIENT owns the charge-up (hold jump) and applies the launch velocity
 * locally the moment the key is released — player movement is
 * client-authoritative, and waiting a network round-trip on a movement key
 * feels laggy. It then reports the blast via {@code RocketBlastPayload}.
 * This class owns everything the client must NOT: billing the battery
 * (proportional to charge), the fall-damage grace window, velocity mirroring
 * (keeps the server's movement bookkeeping plausible), and broadcasting the
 * explosion sound/particles so every nearby player experiences the blast.
 */
public final class RocketBootsHandler {

    // ------------------------------------------------------------------
    // Tunables (client charge machine reads these — single source of truth).
    // ------------------------------------------------------------------

    /** Hold at least this long or the charge fizzles (10 ticks = 0.5 s). */
    public static final int CHARGE_MIN_TICKS = 10;
    /** Full charge after this long (30 ticks = 1.5 s). */
    public static final int CHARGE_FULL_TICKS = 30;

    /** Battery cost of a FULL blast; partial charges bill proportionally (min ~17). */
    public static final int BLAST_COST_FULL = 50;

    /**
     * Upward launch velocity at FULL charge, SET (not added) like vanilla's own
     * jump. 1.34 -> apex ~9.96 blocks (gravity 0.08/tick, drag x0.98).
     * Partial charge scales the APEX linearly, so vy scales with sqrt(fraction)
     * (height goes with velocity squared): minimum blast ~3.3 blocks.
     */
    public static final double VERTICAL_BLAST_VELOCITY = 1.34;

    /**
     * Gliding blast: one hard punch along the look direction at FULL charge.
     * A vanilla firework converges to ~1.7 blocks/tick; 3.4 is "a big firework"
     * — twice the speed, decaying naturally under glide drag. Scales linearly
     * with charge. Far below the server's ~17 blocks/tick glide allowance.
     */
    public static final double GLIDE_BLAST_SPEED = 3.4;

    /** No fall damage for this long after a blast (100 ticks = 5 s). */
    private static final int GRACE_TICKS = 100;
    /** Ignore blast payloads arriving closer together than this (spam guard). */
    private static final int BLAST_SPAM_GUARD_TICKS = 8;

    /** Big red exhaust mote for the blast burst. */
    private static final DustParticleOptions BLAST_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.1f), 1.2f);

    // ------------------------------------------------------------------
    // Per-player state (UUID-keyed, cleaned on logout; no sentinel values —
    // "never blasted" is an ABSENT map entry, which dodges the Long.MIN_VALUE
    // overflow bug that silently killed the old double-jump's cooldown).
    // ------------------------------------------------------------------

    /** Game time until which fall damage is forgiven. */
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
    /** Game time of the last accepted blast. */
    private static final Map<UUID, Long> LAST_BLAST = new HashMap<>();

    private RocketBootsHandler() {}

    // ------------------------------------------------------------------
    // Payload entry point (called by ModNetworking, on the server thread).
    // ------------------------------------------------------------------

    /** A client released a charged blast. Validate, bill, remember, detonate for everyone. */
    public static void handleBlast(ServerPlayer player, float chargeFraction, boolean gliding) {
        long now = player.level().getGameTime();
        Long last = LAST_BLAST.get(player.getUUID());
        if (last != null && now - last < BLAST_SPAM_GUARD_TICKS) {
            return; // duplicate/spam — quietly ignore
        }

        // Never trust the wire: clamp the claimed charge into the legal range.
        float f = Mth.clamp(chargeFraction,
                (float) CHARGE_MIN_TICKS / CHARGE_FULL_TICKS, 1.0f);
        int cost = Math.round(BLAST_COST_FULL * f);

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.is(RedstoneLapisMod.REDSTONE_ROCKET_BOOTS.get())
                || !PoweredGearItem.isPowered(player, boots, cost)
                || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            return; // client mis-predicted (e.g. charge raced to 0) — no bill, no boom
        }

        PoweredGearItem.drainOnePowerTick(player, boots, cost);
        LAST_BLAST.put(player.getUUID(), now);
        GRACE_UNTIL.put(player.getUUID(), now + GRACE_TICKS);

        // Mirror the client's launch on the server-side player: doesn't move
        // them (client-authoritative) but keeps server velocity bookkeeping
        // honest for vanilla's movement checks — like a firework ticking both sides.
        Vec3 dm = player.getDeltaMovement();
        if (gliding && player.isFallFlying()) {
            player.setDeltaMovement(player.getLookAngle().scale(GLIDE_BLAST_SPEED * f));
        } else {
            player.setDeltaMovement(dm.x, VERTICAL_BLAST_VELOCITY * Math.sqrt(f), dm.z);
        }

        detonationEffects(player, f, gliding);
    }

    /** Explosion audio + redstone-themed burst, broadcast to everyone nearby. */
    private static void detonationEffects(ServerPlayer player, float f, boolean gliding) {
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY() + 0.1, z = player.getZ();

        // Layered "redstone pop": a real explosion boom (pitched up so it reads
        // gadget, not TNT) under a firework crack. Volume grows with charge.
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.5F + 0.5F * f, 1.5F);
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.0F, 0.9F);

        // Burst scaled by charge: red dust core + electric sparks + a puff of smoke.
        int dust = (int) (25 + 25 * f);
        level.sendParticles(BLAST_DUST, x, y, z, dust, 0.5, 0.2, 0.5, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, (int) (10 + 10 * f), 0.4, 0.2, 0.4, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.3, 0.1, 0.3, 0.02);
        if (gliding) {
            // Trail the burst behind a gliding player so the punch reads directional.
            Vec3 tail = player.position().subtract(player.getLookAngle().scale(1.2));
            level.sendParticles(BLAST_DUST, tail.x, tail.y, tail.z, 12, 0.3, 0.3, 0.3, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Fall-damage grace window.
    // ------------------------------------------------------------------

    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Long until = GRACE_UNTIL.get(player.getUUID());
            if (until != null && player.level().getGameTime() <= until) {
                event.setCanceled(true); // your own rocketry never hurts you
            }
        }
    }

    // ------------------------------------------------------------------
    // Cleanup.
    // ------------------------------------------------------------------

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GRACE_UNTIL.remove(id);
        LAST_BLAST.remove(id);
    }
}
