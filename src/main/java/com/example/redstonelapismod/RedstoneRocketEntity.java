package com.example.redstonelapismod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The launcher's projectile — this mod's first true Entity. The server owns
 * the real one (spawns it, moves it, decides the hit); vanilla's entity
 * tracker mirrors it to every nearby client, so all players see the same
 * shot without any custom networking.
 *
 * One entity class serves EVERY rocket type: the launcher stuffs the fired
 * ammo stack into this entity ({@code setItem}), the base class syncs that
 * stack to clients, and all behavior — gravity, trail, payload — is asked of
 * the ammo item via {@link #ammo()}. New rocket type = new item subclass;
 * this file never changes.
 */
public class RedstoneRocketEntity extends ThrowableItemProjectile {

    /**
     * The network constructor — the game calls this when a client first
     * learns the entity exists, or when one loads from disk.
     */
    public RedstoneRocketEntity(EntityType<? extends RedstoneRocketEntity> type, Level level) {
        super(type, level);
    }

    /** The gameplay constructor — spawns at the shooter's eyes, owned by them. */
    public RedstoneRocketEntity(Level level, LivingEntity shooter) {
        super(RedstoneLapisMod.REDSTONE_ROCKET_ENTITY.get(), shooter, level);
    }

    /** Fallback sprite/behavior if no ammo stack was set: the standard rocket. */
    @Override
    protected Item getDefaultItem() {
        return RedstoneLapisMod.REDSTONE_ROCKET.get();
    }

    /**
     * The ammo item steering this flight. getItem() is the synced stack the
     * launcher loaded, so server and every client all agree on the answer.
     */
    public RocketAmmoItem ammo() {
        return this.getItem().getItem() instanceof RocketAmmoItem rocketAmmo
                ? rocketAmmo
                : (RocketAmmoItem) RedstoneLapisMod.REDSTONE_ROCKET.get();
    }

    @Override
    protected double getDefaultGravity() {
        return ammo().gravity();
    }

    @Override
    public void tick() {
        super.tick(); // inherited: move, drag, gravity, hit detection

        if (this.level().isClientSide) {
            ammo().clientTrail(this); // each client draws its own trail
        } else if (this.tickCount > ammo().lifetimeTicks()) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            ammo().onImpact(this, result); // the payload lives on the ammo item
            this.discard();
        }
    }
}
