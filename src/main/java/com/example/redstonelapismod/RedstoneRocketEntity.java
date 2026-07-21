package com.example.redstonelapismod;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The Redstone Rocket in flight — this mod's first true Entity. The server
 * owns the real rocket (spawns it, moves it, decides the hit); vanilla's
 * entity tracker mirrors it to every nearby client, so all players see the
 * same shot without any custom networking.
 *
 * Extends {@link ThrowableItemProjectile} (the snowball/ender-pearl family):
 * movement, drag, hit detection, and syncing are inherited; we supply the
 * sprite ({@link #getDefaultItem}), a flatter gravity, a dust trail, and the
 * concussion blast on impact.
 */
public class RedstoneRocketEntity extends ThrowableItemProjectile {

    /** Blast strength — creeper is 3.0; ours trades block damage for pure concussion. */
    public static final float BLAST_POWER = 2.5F;

    /** Safety net: a rocket that never hits anything vanishes after 10 seconds. */
    private static final int MAX_LIFETIME_TICKS = 200;

    /** Trail mote: redstone-red dust, same family as the boots' exhaust. */
    private static final DustParticleOptions TRAIL_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.0F);

    /**
     * The registration/network constructor — the game calls this when a client
     * first learns the entity exists, or when one loads from disk.
     */
    public RedstoneRocketEntity(EntityType<? extends RedstoneRocketEntity> type, Level level) {
        super(type, level);
    }

    /** The gameplay constructor — spawns at the shooter's eyes, owned by them. */
    public RedstoneRocketEntity(Level level, LivingEntity shooter) {
        super(RedstoneLapisMod.REDSTONE_ROCKET_ENTITY.get(), shooter, level);
    }

    /** Which item's sprite the renderer draws for this projectile. */
    @Override
    protected Item getDefaultItem() {
        return RedstoneLapisMod.REDSTONE_ROCKET.get();
    }

    /** Half a snowball's droop (0.03) — near-flat direct fire over ~20 blocks. */
    @Override
    protected double getDefaultGravity() {
        return 0.015;
    }

    @Override
    public void tick() {
        super.tick(); // inherited: move, drag, gravity, hit detection

        if (this.level().isClientSide) {
            // Trail: each client near the rocket ticks its own mirror copy, so
            // everyone renders this locally — multiplayer-visible for free.
            // The rocket covers ~3 blocks/tick; lay motes along the path so the
            // trail is a line, not dots.
            Vec3 motion = this.getDeltaMovement();
            for (int i = 0; i < 4; i++) {
                double back = i / 4.0;
                this.level().addParticle(TRAIL_DUST,
                        this.getX() - motion.x * back,
                        this.getY() - motion.y * back,
                        this.getZ() - motion.z * back,
                        0.0, 0.0, 0.0);
            }
        } else if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            // ExplosionInteraction.NONE = full entity damage + knockback, zero
            // block damage (maps to BlockInteraction.KEEP). Explosion visuals
            // and the boom sound come with it.
            this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                    BLAST_POWER, Level.ExplosionInteraction.NONE);
            this.discard();
        }
    }
}
