package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;
import com.example.redstonelapismod.RocketBootsHandler;
import com.example.redstonelapismod.network.RocketBlastPayload;

import com.mojang.blaze3d.platform.InputConstants;

import org.joml.Vector3f;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client half of the Redstone Rocket Boots: the hold-to-charge machine.
 *
 * Hold the vanilla JUMP key while wearing powered boots to charge a redstone
 * blast; release to fire. Gliding on an elytra -> the blast punches you
 * FORWARD (one big firework-style kick); otherwise -> straight UP. Held less
 * than the minimum (0.5 s) -> fizzle, no blast, no cost.
 *
 * The launch velocity is applied HERE, instantly on release — player movement
 * is client-authoritative and a network round-trip on a movement key feels
 * laggy — then {@code RocketBlastPayload} tells the server, which bills the
 * battery and detonates the effects for everyone.
 *
 * Input plumbing subtlety: holding space on the ground normally makes the
 * player bunny-hop. While charging on the ground we force the jump KeyMapping
 * to "released" every tick BEFORE vanilla samples input (ClientTickEvent.Pre),
 * so you plant your feet and charge instead of hopping. Because that lies to
 * {@code isDown()}, the charge machine reads the PHYSICAL key state straight
 * from the keyboard (GLFW) instead.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class RocketBootsClientHandler {

    /** Small red mote sprinkled while charging (client-local; the blast itself is server-broadcast). */
    private static final DustParticleOptions CHARGE_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.25f, 0.1f), 0.5f);

    /** Ticks the jump key has been held for the current charge; 0 = not charging. */
    private static int chargeTicks;
    /** Physical key state last tick, for fresh-press edge detection. */
    private static boolean prevRawDown;

    private RocketBootsClientHandler() {}

    /** Charge progress 0..1 for the HUD (0 while not charging). */
    public static float chargeFraction() {
        return Math.min(1.0f, (float) chargeTicks / RocketBootsHandler.CHARGE_FULL_TICKS);
    }

    // ------------------------------------------------------------------
    // Pre-tick: suppress the vanilla hop BEFORE input is sampled this tick.
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (chargeTicks > 0 && mc.player != null && mc.player.onGround()) {
            mc.options.keyJump.setDown(false); // vanilla sees "released" -> no bunny-hop while charging
        }
    }

    // ------------------------------------------------------------------
    // Post-tick: the charge state machine.
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            chargeTicks = 0;
            prevRawDown = false;
            return; // left the world; server clears its side on logout
        }

        boolean rawDown = isJumpPhysicallyDown(mc);
        boolean freshPress = rawDown && !prevRawDown;
        prevRawDown = rawDown;

        boolean valid = mc.screen == null // no charging (or firing) from inside menus
                && GogglesClient.isWearingPowered(player,
                        RedstoneLapisMod.REDSTONE_ROCKET_BOOTS.get(), EquipmentSlot.FEET)
                && !player.isSpectator() && !player.isPassenger()
                && !player.isInWater() && !player.getAbilities().flying;

        if (chargeTicks == 0) {
            // Idle -> a FRESH press while valid starts a charge. (Fresh, so holding
            // space from before equipping the boots doesn't spontaneously charge.)
            if (freshPress && valid) {
                chargeTicks = 1;
            }
            return;
        }

        // --- Currently charging. ---
        if (!valid) {
            chargeTicks = 0; // boots off / menu opened / water... : cancel silently
            return;
        }
        if (rawDown) {
            chargeTicks = Math.min(chargeTicks + 1, RocketBootsHandler.CHARGE_FULL_TICKS);
            chargeParticles(player);
            return;
        }

        // --- Key released: fire or fizzle. ---
        int held = chargeTicks;
        chargeTicks = 0;
        if (held < RocketBootsHandler.CHARGE_MIN_TICKS) {
            return; // fizzle: too short, no blast, no cost
        }
        float f = Math.min(1.0f, (float) held / RocketBootsHandler.CHARGE_FULL_TICKS);
        boolean gliding = player.isFallFlying();

        // Launch NOW (zero latency)...
        Vec3 dm = player.getDeltaMovement();
        if (gliding) {
            // Forward punch along the gaze, like one big firework blast.
            player.setDeltaMovement(player.getLookAngle()
                    .scale(RocketBootsHandler.GLIDE_BLAST_SPEED * f));
        } else {
            // Straight up; horizontal momentum preserved. sqrt: apex height goes
            // with velocity SQUARED, so this makes height scale linearly with charge.
            player.setDeltaMovement(dm.x,
                    RocketBootsHandler.VERTICAL_BLAST_VELOCITY * Math.sqrt(f), dm.z);
        }

        // ...then report; the server bills, grants fall grace, and detonates
        // the sound/particles for everyone (a mis-predicted blast is a harmless
        // client-only hop the server simply doesn't bill or broadcast).
        PacketDistributor.sendToServer(new RocketBlastPayload(f, gliding));
    }

    /**
     * Physical keyboard state of the jump binding, bypassing KeyMapping's
     * bookkeeping (which our Pre-tick suppression deliberately falsifies).
     * Falls back to isDown() for non-keyboard bindings (e.g. mouse buttons).
     */
    private static boolean isJumpPhysicallyDown(Minecraft mc) {
        KeyMapping jump = mc.options.keyJump;
        InputConstants.Key key = jump.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return jump.isDown();
    }

    /** Rising sparkle at the feet while charging — feedback you can feel. */
    private static void chargeParticles(LocalPlayer player) {
        // Every 4 ticks early on, every 2 near full: the crackle accelerates.
        int interval = chargeTicks >= RocketBootsHandler.CHARGE_FULL_TICKS ? 2 : 4;
        if (player.tickCount % interval == 0) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            player.level().addParticle(CHARGE_DUST,
                    player.getX() + Math.cos(angle) * 0.4,
                    player.getY() + 0.05,
                    player.getZ() + Math.sin(angle) * 0.4,
                    0.0, 0.05, 0.0);
        }
    }
}
