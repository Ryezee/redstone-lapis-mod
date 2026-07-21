package com.example.redstonelapismod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server half of the Echo Lens: the expanding sonar pulse.
 *
 * A pulse is pure position + age — it belongs to a POINT in the world, not to
 * the player who cast it (they can walk away; the wave keeps going). Each
 * server tick every active pulse grows by half a block: a blue particle ring
 * marks the wavefront for everyone nearby, and any mob the front crosses this
 * tick gets the vanilla Glowing outline (visible through walls) plus a sonar
 * contact chime at its position. The staggered reveal — near things ping
 * first, far things later — is the whole feel of the gadget.
 *
 * Everything here is server-side: sendParticles/playSound broadcast to every
 * player in range, and the Glowing effect syncs automatically, so multiplayer
 * works for free.
 */
public final class EchoPulseHandler {

    /** Ticks for the pulse to reach full radius (20 = 1 s, half a block per tick). */
    private static final int PULSE_TICKS = 20;
    /** How long revealed mobs stay outlined (80 = 4 s). */
    private static final int GLOW_TICKS = 80;

    /** Lapis-blue wavefront ring. */
    private static final DustParticleOptions RING_DUST =
            new DustParticleOptions(new Vector3f(0.25f, 0.42f, 0.91f), 1.1f);
    /** Brighter flash on a revealed mob. */
    private static final DustParticleOptions CONTACT_DUST =
            new DustParticleOptions(new Vector3f(0.60f, 0.75f, 1.00f), 1.3f);

    /** All pulses currently expanding, across all dimensions. */
    private static final List<Pulse> ACTIVE = new ArrayList<>();

    /** One expanding wave: where it lives, where it started, how old it is. */
    private static final class Pulse {
        final ServerLevel level;
        final Vec3 center;
        int age;

        Pulse(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }
    }

    private EchoPulseHandler() {}

    /** Called by EchoLensItem.use on the server when a scan fires. */
    public static void startPulse(ServerLevel level, Vec3 center) {
        ACTIVE.add(new Pulse(level, center));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Pulse> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Pulse pulse = iterator.next();
            pulse.age++;
            double radius = EchoLensItem.SCAN_RADIUS * pulse.age / PULSE_TICKS;
            double previousRadius = EchoLensItem.SCAN_RADIUS * (pulse.age - 1) / PULSE_TICKS;
            spawnRing(pulse.level, pulse.center, radius);
            revealBand(pulse.level, pulse.center, previousRadius, radius);
            if (pulse.age >= PULSE_TICKS) {
                iterator.remove();
            }
        }
    }

    /**
     * Singleplayer quits and re-creates servers within one game session; a
     * pulse must never outlive the server (and level) it was cast in.
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    /** Blue dust circle at the wavefront, one particle per ~1 block of arc. */
    private static void spawnRing(ServerLevel level, Vec3 center, double radius) {
        int points = Math.max(10, (int) (2.0 * Math.PI * radius));
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            level.sendParticles(RING_DUST,
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Reveals every mob the wavefront crossed THIS tick (distance in (prev, now]). */
    private static void revealBand(ServerLevel level, Vec3 center, double previousRadius, double radius) {
        AABB searchBox = new AABB(center, center).inflate(radius + 1.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            double distance = mob.position().distanceTo(center);
            if (distance <= previousRadius || distance > radius) {
                continue; // not crossed by the front this tick
            }
            // false, false = no potion swirls; the Glowing OUTLINE still renders.
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
            level.sendParticles(CONTACT_DUST,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    10, 0.3, 0.4, 0.3, 0.0);
            // High short bell — a sonar "contact" you can hear directionally.
            level.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS, 0.6F, 1.8F);
        }
    }
}
