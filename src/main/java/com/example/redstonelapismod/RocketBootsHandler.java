package com.example.redstonelapismod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server half of the Redstone Rocket Boots.
 *
 * Player movement is CLIENT-authoritative: the wearer's own machine applies
 * the jump impulse / glide thrust instantly for zero-latency feel (see
 * client/RocketBootsClientHandler) and tells us via the two payloads in the
 * network package. Our job here is everything the client must NOT own:
 * billing the battery, the fall-damage grace window, and broadcasting the
 * launch/exhaust effects so OTHER players see and hear the rockets.
 * We also mirror the thrust math onto the server-side player so its velocity
 * bookkeeping stays plausible for vanilla's movement checks — exactly what a
 * real firework entity does (it ticks on both sides).
 */
public final class RocketBootsHandler {

    // ------------------------------------------------------------------
    // Tunables (kept in sync with client/RocketBootsClientHandler).
    // ------------------------------------------------------------------

    /** Charge billed per rocket jump (battery holds 1000 -> 40 jumps). */
    public static final int JUMP_COST = 25;
    /** Bill THRUST_DRAIN_AMOUNT every N ticks of held thrust (5 per 4 ticks = 25/s). */
    public static final int THRUST_DRAIN_INTERVAL_TICKS = 4;
    public static final int THRUST_DRAIN_AMOUNT = 5;
    /** No fall damage for this long after a rocket jump or thrust (100 = 5 s). */
    private static final int GRACE_TICKS = 100;
    /** Ignore jump payloads arriving within this many ticks of the last one. */
    private static final int JUMP_COOLDOWN_TICKS = 10;

    /**
     * Per-tick glide thrust, same shape as a vanilla firework's boost:
     * dm += look * ADDITIVE + (look * TARGET_SPEED - dm) * PULL.
     * Vanilla firework: ADDITIVE 0.1, TARGET_SPEED 1.5, PULL 0.5 (top speed
     * ~1.7 blocks/tick). Ours targets 2.25 -> ~2.5 blocks/tick: noticeably
     * stronger, still far under the server's ~17 blocks/tick glide allowance.
     */
    public static final double THRUST_ADDITIVE = 0.15;
    public static final double THRUST_TARGET_SPEED = 2.25;
    public static final double THRUST_PULL = 0.5;

    /** Red rocket-exhaust mote (same family as the headtorch beam). */
    private static final DustParticleOptions EXHAUST_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.1f), 0.8f);

    // ------------------------------------------------------------------
    // Per-player state (UUID-keyed, cleaned up on logout).
    // ------------------------------------------------------------------

    /** Players currently holding thrust (set/cleared by RocketThrustPayload). */
    private static final Set<UUID> THRUSTING = new HashSet<>();
    /** Game time (server tick stamp) until which fall damage is forgiven. */
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
    /** Game time of each player's last accepted rocket jump (cooldown). */
    private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();

    private RocketBootsHandler() {}

    // ------------------------------------------------------------------
    // Payload entry points (called by ModNetworking, on the server thread).
    // ------------------------------------------------------------------

    /** A client says it rocket-jumped. Validate, bill, remember, show everyone. */
    public static void handleJump(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_JUMP.get(player.getUUID());
        if (last != null && now - last < JUMP_COOLDOWN_TICKS) {
            return; // spam/duplicate — quietly ignore
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.is(RedstoneLapisMod.REDSTONE_ROCKET_BOOTS.get())
                || !PoweredGearItem.isPowered(player, boots, JUMP_COST)
                || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            return; // client mis-predicted (e.g. charge raced to 0) — no bill, no effects
        }

        PoweredGearItem.drainOnePowerTick(player, boots, JUMP_COST);
        LAST_JUMP.put(player.getUUID(), now);
        GRACE_UNTIL.put(player.getUUID(), now + GRACE_TICKS);

        // Launch effects, visible/audible to everyone nearby (multiplayer rule).
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(EXHAUST_DUST, player.getX(), player.getY() + 0.1, player.getZ(),
                20, 0.25, 0.1, 0.25, 0.0);
        level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 0.1, player.getZ(),
                12, 0.15, 0.05, 0.15, 0.08);
    }

    /** A client started/stopped holding thrust while gliding. */
    public static void handleThrust(ServerPlayer player, boolean thrusting) {
        if (thrusting && player.isFallFlying() && !player.isSpectator()) {
            THRUSTING.add(player.getUUID());
        } else {
            stopThrust(player);
        }
    }

    // ------------------------------------------------------------------
    // Server tick: apply/bill active thrust.
    // ------------------------------------------------------------------

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !THRUSTING.contains(player.getUUID())) {
            return;
        }

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        boolean valid = player.isFallFlying()
                && boots.is(RedstoneLapisMod.REDSTONE_ROCKET_BOOTS.get())
                && PoweredGearItem.isPowered(player, boots, 1);
        if (!valid) {
            stopThrust(player); // glide ended / boots off / battery dead
            return;
        }

        // Mirror the client's thrust math on the server-side player: keeps the
        // server's velocity bookkeeping honest (anti-cheat headroom, fall math),
        // exactly like a vanilla firework ticking on both sides. NOT synced back
        // to the client (no hurtMarked) — the client runs its own copy.
        Vec3 look = player.getLookAngle();
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.add(
                look.x * THRUST_ADDITIVE + (look.x * THRUST_TARGET_SPEED - dm.x) * THRUST_PULL,
                look.y * THRUST_ADDITIVE + (look.y * THRUST_TARGET_SPEED - dm.y) * THRUST_PULL,
                look.z * THRUST_ADDITIVE + (look.z * THRUST_TARGET_SPEED - dm.z) * THRUST_PULL));

        // Bill 5 charge every 4 ticks (= 25/s, ~40 s of thrust per battery).
        if (player.tickCount % THRUST_DRAIN_INTERVAL_TICKS == 0) {
            PoweredGearItem.drainOnePowerTick(player, boots, THRUST_DRAIN_AMOUNT);
        }

        // Rolling grace: as long as you're thrusting (and shortly after), no fall damage.
        GRACE_UNTIL.put(player.getUUID(), player.level().getGameTime() + GRACE_TICKS);

        // Exhaust trail behind the feet, every other tick, for everyone to see.
        if (player.tickCount % 2 == 0) {
            Vec3 tail = player.position().subtract(look.scale(0.6));
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.FIREWORK, tail.x, tail.y, tail.z,
                    3, 0.1, 0.1, 0.1, 0.05);
            level.sendParticles(EXHAUST_DUST, tail.x, tail.y, tail.z,
                    2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private static void stopThrust(ServerPlayer player) {
        if (THRUSTING.remove(player.getUUID())) {
            // Cover the landing that follows the boost.
            GRACE_UNTIL.put(player.getUUID(),
                    player.level().getGameTime() + GRACE_TICKS);
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
        THRUSTING.remove(id);
        GRACE_UNTIL.remove(id);
        LAST_JUMP.remove(id);
    }
}
