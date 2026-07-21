package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;
import com.example.redstonelapismod.RocketBootsHandler;
import com.example.redstonelapismod.network.RocketJumpPayload;
import com.example.redstonelapismod.network.RocketThrustPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client half of the Redstone Rocket Boots: reads the vanilla JUMP key (no new
 * keybind — a fresh press in mid-air is the rocket jump, holding it while
 * elytra-gliding is thrust) and applies the motion LOCALLY, instantly.
 *
 * Why local: player movement is client-authoritative — this machine simulates
 * the player and reports positions; waiting for the server to push velocity
 * back would add a perceptible round-trip on a feel-critical key. So we move
 * first and notify the server (payloads in the network package), which bills
 * the battery, validates, and shows the effects to everyone else. A vanilla
 * firework boosts gliders the same both-sides way.
 *
 * Input subtlety, handled by ordering: pressing jump while falling is ALSO how
 * vanilla deploys an elytra. Vanilla processes input before this handler runs
 * (ClientTickEvent.Post), so a deploy-press has already set isFallFlying by
 * the time we look — that press routes to "thrust if held", never to a rocket
 * jump. Releasing and pressing again mid-glide stays thrust; rocket jumps
 * only ever fire when NOT gliding.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class RocketBootsClientHandler {

    /**
     * Initial upward velocity of a rocket jump, SET (not added) like vanilla's
     * own jumpFromGround. 1.34 -> apex ~9.96 blocks under gravity 0.08/tick and
     * 0.98 drag; kept a hair under 10 so an unprotected flat-ground return is
     * 7 damage, not 8 (SAFE_FALL_DISTANCE 3 + ceil).
     */
    private static final double JUMP_VELOCITY = 1.34;
    /** Client-side echo of the server's jump cooldown (ticks). */
    private static final int JUMP_COOLDOWN_TICKS = 10;

    /** Previous tick's jump-key state, for fresh-press edge detection. */
    private static boolean prevJumpDown;
    /** Whether we currently believe we're thrusting (mirrors what we told the server). */
    private static boolean thrusting;
    /** Client tick of the last rocket jump we fired. */
    private static long lastJumpTick = Long.MIN_VALUE;
    private static long ticks;

    private RocketBootsClientHandler() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            // Left the world: forget everything; the server clears its side on logout.
            prevJumpDown = false;
            thrusting = false;
            return;
        }

        // Fresh-press edge detector on the VANILLA jump key. isDown() is already
        // false while a GUI/screen is open, so menu presses can't leak in here.
        boolean down = mc.options.keyJump.isDown();
        boolean freshPress = down && !prevJumpDown;
        prevJumpDown = down;

        boolean wearingPowered = GogglesClient.isWearingPowered(player,
                RedstoneLapisMod.REDSTONE_ROCKET_BOOTS.get(), EquipmentSlot.FEET);

        // --- Thrust state machine: hold jump while gliding -> continuous thrust.
        boolean shouldThrust = down && player.isFallFlying() && wearingPowered
                && !player.isSpectator();
        if (shouldThrust != thrusting) {
            thrusting = shouldThrust;
            // Tell the server only on CHANGE; it ticks the flag on its side.
            PacketDistributor.sendToServer(new RocketThrustPayload(thrusting));
        }
        if (thrusting) {
            applyThrustTick(player);
            return; // a tick spent thrusting is never also a rocket jump
        }

        // --- Rocket jump: a fresh mid-air press while NOT gliding.
        if (freshPress && wearingPowered
                && !player.onGround()          // mid-air only: that's the "double" in double-jump
                && !player.isFallFlying()      // gliding presses belong to thrust above
                && !player.isInWater() && !player.isInLava()
                && !player.getAbilities().flying // creative flight has its own physics
                && !player.isSpectator() && !player.isPassenger()
                && ticks - lastJumpTick >= JUMP_COOLDOWN_TICKS) {
            lastJumpTick = ticks;
            // Move NOW (zero latency), preserving horizontal momentum...
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, JUMP_VELOCITY, dm.z);
            // ...then tell the server, which bills the battery and shows the
            // launch to everyone. If it disagrees (charge raced to 0), our hop
            // was a harmless client-only misprediction.
            PacketDistributor.sendToServer(RocketJumpPayload.INSTANCE);
        }
    }

    /**
     * Same per-tick acceleration the server mirrors (constants shared from
     * RocketBootsHandler): the vanilla firework curve with a higher target
     * speed. Applied here because THIS simulation is the one that moves us.
     */
    private static void applyThrustTick(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.add(
                look.x * RocketBootsHandler.THRUST_ADDITIVE
                        + (look.x * RocketBootsHandler.THRUST_TARGET_SPEED - dm.x) * RocketBootsHandler.THRUST_PULL,
                look.y * RocketBootsHandler.THRUST_ADDITIVE
                        + (look.y * RocketBootsHandler.THRUST_TARGET_SPEED - dm.y) * RocketBootsHandler.THRUST_PULL,
                look.z * RocketBootsHandler.THRUST_ADDITIVE
                        + (look.z * RocketBootsHandler.THRUST_TARGET_SPEED - dm.z) * RocketBootsHandler.THRUST_PULL));
    }
}
