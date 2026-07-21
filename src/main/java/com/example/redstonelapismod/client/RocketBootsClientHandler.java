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
 * Input plumbing subtlety: on the ground, charging requires SNEAK + jump —
 * plain space is always a normal instant jump (tap-vs-hold is unknowable at
 * press time, so grounded charging needs its own gesture). Mid-air, any jump
 * hold charges. The whole state machine runs in ClientTickEvent.Pre — BEFORE
 * vanilla samples input each tick — so the press that starts a charge is
 * suppressed the same tick it lands and never causes a hop. Because the
 * suppression lies to {@code isDown()}, the machine reads the PHYSICAL key
 * state straight from the keyboard (GLFW) instead.
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
    // Pre-tick: the ENTIRE charge state machine. It must run in Pre — before
    // vanilla samples input this tick — or the first press of a charge reaches
    // vanilla and the player hops before charging starts (the v2.0 race).
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
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
            // Idle -> a FRESH press while valid starts a charge — but on the
            // ground only while SNEAKING, so plain space stays a normal jump.
            // Mid-air there is no jump to protect, so any hold charges.
            // (Fresh, so holding space from before equipping the boots doesn't
            // spontaneously charge.)
            boolean gateOpen = !player.onGround() || player.isShiftKeyDown();
            if (freshPress && valid && gateOpen) {
                chargeTicks = 1;
                suppressGroundJump(mc, player); // same tick: vanilla never sees the press
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
            suppressGroundJump(mc, player);
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
     * While charging on the ground, force the jump KeyMapping to "released"
     * so vanilla's input sampling (which runs after Pre) never sees it — no
     * bunny-hop, no sneak-jump. Mid-air the key is left alone so an elytra
     * can still deploy on the same held press.
     */
    private static void suppressGroundJump(Minecraft mc, LocalPlayer player) {
        if (player.onGround()) {
            mc.options.keyJump.setDown(false);
        }
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
