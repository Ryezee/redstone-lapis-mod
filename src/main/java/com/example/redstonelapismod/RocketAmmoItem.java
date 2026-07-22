package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Base class for everything the Redstone Rocket Launcher can fire. The
 * launcher and the in-flight entity are deliberately dumb: they ask the
 * loaded ammo item for every behavior — how fast, how it falls, what trail
 * it draws, and what happens on impact. Each rocket type overrides the
 * methods it wants to change (Java methods are virtual by default — this is
 * plain polymorphism, no C++ `virtual` keyword needed).
 *
 * The defaults below ARE the standard Redstone Rocket: flat-ish flight and
 * a concussion blast that leaves blocks intact.
 */
public class RocketAmmoItem extends Item {

    /** Default blast strength — creeper is 3.0; trades block damage for pure concussion. */
    public static final float BLAST_POWER = 2.5F;

    /** Default trail mote: redstone-red dust. */
    private static final DustParticleOptions TRAIL_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.0F);

    public RocketAmmoItem(Properties properties) {
        super(properties);
    }

    // --- Flight profile (read by launcher + entity) ---

    /** Muzzle velocity in blocks/tick (arrow at full draw is 3.0). */
    public float speed() {
        return 3.0F;
    }

    /** Spread — 0 is laser-perfect; snowballs use 1.0. */
    public float inaccuracy() {
        return 0.25F;
    }

    /** Downward pull per tick (snowball is 0.03). */
    public double gravity() {
        return 0.015;
    }

    /** Server-side safety net: the projectile vanishes after this many ticks. */
    public int lifetimeTicks() {
        return 200;
    }

    /** Launcher cooldown after firing this type. */
    public int cooldownTicks() {
        return 30;
    }

    // --- Presentation ---

    public SoundEvent fireSound() {
        return SoundEvents.FIREWORK_ROCKET_LAUNCH;
    }

    public float firePitch() {
        return 0.7F;
    }

    /**
     * Called every tick ON EACH CLIENT tracking the projectile — draw the
     * trail here. Default: red dust laid along the last tick of movement.
     */
    public void clientTrail(RedstoneRocketEntity rocket) {
        Vec3 motion = rocket.getDeltaMovement();
        for (int i = 0; i < 4; i++) {
            double back = i / 4.0;
            rocket.level().addParticle(TRAIL_DUST,
                    rocket.getX() - motion.x * back,
                    rocket.getY() - motion.y * back,
                    rocket.getZ() - motion.z * back,
                    0.0, 0.0, 0.0);
        }
    }

    /**
     * Called ONCE on the server when the projectile hits something; the
     * entity discards itself right after. This is the payload.
     */
    public void onImpact(RedstoneRocketEntity rocket, HitResult result) {
        // ExplosionInteraction.NONE = full entity damage + knockback, zero
        // block damage (maps to BlockInteraction.KEEP).
        rocket.level().explode(rocket, rocket.getX(), rocket.getY(), rocket.getZ(),
                BLAST_POWER, Level.ExplosionInteraction.NONE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.rocket_ammo.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
