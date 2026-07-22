package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Redstone Mega Nuke Rocket — the top of the escalation chain. A heavy,
 * slow shell built around the Redstone Nuclear Warhead; on impact it levels
 * terrain (blast 12.0 — triple TNT) under a full particle mushroom cloud
 * with thunder layered over the boom.
 */
public class MegaNukeRocketItem extends RocketAmmoItem {

    /** TNT is 4.0, our Mega 6.0 — this digs a genuine crater. */
    public static final float NUKE_BLAST_POWER = 12.0F;

    /** Fallout glow: max-size orange-red dust. */
    private static final DustParticleOptions ASH_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.3F, 0.1F), 4.0F);

    public MegaNukeRocketItem(Properties properties) {
        super(properties);
    }

    // --- Heavy shell flight profile ---

    @Override
    public float speed() {
        return 2.2F;   // lumbering compared to the standard rocket's 3.0
    }

    @Override
    public double gravity() {
        return 0.02;   // noticeably droops — lob it, don't snipe with it
    }

    /** Four-second reload; this is not a spam weapon. */
    @Override
    public int cooldownTicks() {
        return 80;
    }

    /** The deepest launch thump of the family. */
    @Override
    public float firePitch() {
        return 0.4F;
    }

    /** Burning-fuse trail: flame line plus a smoke wake. */
    @Override
    public void clientTrail(RedstoneRocketEntity rocket) {
        Vec3 motion = rocket.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            double back = i / 3.0;
            rocket.level().addParticle(ParticleTypes.FLAME,
                    rocket.getX() - motion.x * back,
                    rocket.getY() - motion.y * back,
                    rocket.getZ() - motion.z * back,
                    0.0, 0.0, 0.0);
        }
        rocket.level().addParticle(ParticleTypes.LARGE_SMOKE,
                rocket.getX(), rocket.getY(), rocket.getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    public void onImpact(RedstoneRocketEntity rocket, HitResult result) {
        Level level = rocket.level();
        double x = rocket.getX();
        double y = rocket.getY();
        double z = rocket.getZ();

        // The crater: real block destruction, TNT drop rules.
        level.explode(rocket, x, y, z, NUKE_BLAST_POWER, Level.ExplosionInteraction.TNT);

        if (level instanceof ServerLevel serverLevel) {
            // Ground flash + fallout glow.
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 6, 3.0, 2.0, 3.0, 0.0);
            serverLevel.sendParticles(ASH_DUST, x, y, z, 300, 6.0, 3.0, 6.0, 0.0);
            // Mushroom stem: smoke column rising 14 blocks.
            for (int height = 0; height <= 14; height += 2) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, y + height, z, 20, 1.2, 1.0, 1.2, 0.02);
            }
            // Mushroom cap: wide slow-rising cloud above the stem.
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y + 16, z, 80, 5.0, 1.5, 5.0, 0.01);

            // Layered report, loud enough to carry: boom + rolling thunder.
            // GENERIC_EXPLODE is a Holder in 1.21 — unwrap with value().
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 8.0F, 0.5F);
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.PLAYERS, 8.0F, 0.5F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.nuke_rocket.blast")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.nuke_rocket.warning")
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
