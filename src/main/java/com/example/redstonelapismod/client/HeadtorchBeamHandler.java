package com.example.redstonelapismod.client;

import org.joml.Vector3f;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Draws the headtorch's visible beam: red dust motes scattered through a 60°
 * cone along the wearer's gaze. Pure vanilla particles (same engine as the ore
 * sparkles), so it is shader-safe by construction. The actual illumination is
 * the server-side light spot in HeadtorchHandler — this is only the visual.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class HeadtorchBeamHandler {
    /** Spawn a pulse of motes every N ticks. */
    private static final int BEAM_INTERVAL_TICKS = 2;
    /** Motes per pulse. */
    private static final int BEAM_PARTICLES_PER_PULSE = 8;
    /** Beam length in blocks — matches HeadtorchHandler.SPOT_RANGE so motes
     *  never sparkle beyond where the lamp can actually cast light. */
    private static final double BEAM_RANGE = 4.0;
    /** Half-angle of the cone: 30° each side of the gaze = 60° total. */
    private static final double BEAM_HALF_ANGLE_DEG = 30.0;
    /** Redstone-red mote, slightly smaller than the ore sparkles. */
    private static final DustParticleOptions BEAM_MOTE =
            new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 0.6f);

    private static int ticks;

    private HeadtorchBeamHandler() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % BEAM_INTERVAL_TICKS != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        // Every player near enough for the client to see, not just ourselves —
        // so other people's headtorch beams render too. Known gap: a remote
        // wearer powered ONLY by a loose pocket battery shows no beam, because
        // other players' inventories are never synced to us (their equipped
        // gear and its charge component are).
        for (Player player : level.players()) {
            if (GogglesClient.isWearingPowered(player,
                    RedstoneLapisMod.REDSTONE_MINER_HEADTORCH.get())) {
                spawnBeam(level, player);
            }
        }
    }

    /** One pulse of cone motes along {@code player}'s gaze. */
    private static void spawnBeam(ClientLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle(); // unit vector of the gaze

        // Build an orthonormal basis around the look axis so we can aim motes
        // anywhere inside the cone: look = "forward", right & up span the disc.
        Vec3 worldUp = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        RandomSource rng = level.random;
        double maxTheta = Math.toRadians(BEAM_HALF_ANGLE_DEG);
        for (int i = 0; i < BEAM_PARTICLES_PER_PULSE; i++) {
            // sqrt makes motes fill the cone's cross-section evenly instead of
            // clumping on the center line.
            double theta = maxTheta * Math.sqrt(rng.nextDouble()); // tilt away from gaze
            double phi = rng.nextDouble() * Math.PI * 2.0;         // spin around gaze
            double sin = Math.sin(theta);
            Vec3 dir = look.scale(Math.cos(theta))
                    .add(right.scale(sin * Math.cos(phi)))
                    .add(up.scale(sin * Math.sin(phi)));
            double dist = 1.0 + rng.nextDouble() * (BEAM_RANGE - 1.0);
            Vec3 p = eye.add(dir.scale(dist));
            level.addParticle(BEAM_MOTE, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }
}
