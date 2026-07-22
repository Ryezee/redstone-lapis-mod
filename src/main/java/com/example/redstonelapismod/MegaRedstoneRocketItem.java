package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Mega Redstone Rocket — the terrain-wrecker. Same flight as the standard
 * rocket but the payload is a TNT-plus blast (6.0 vs TNT's 4.0) with REAL
 * block damage, wrapped in a nova of maximum-size redstone dust.
 */
public class MegaRedstoneRocketItem extends RocketAmmoItem {

    /** TNT is 4.0, creeper 3.0 — this levels terrain. */
    public static final float MEGA_BLAST_POWER = 6.0F;

    /** Scale 4.0 is the dust particle's maximum — each mote is a red bloom. */
    private static final DustParticleOptions NOVA_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.15F), 4.0F);

    /** Thicker in-flight trail than the standard rocket. */
    private static final DustParticleOptions HEAVY_TRAIL =
            new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.15F), 2.0F);

    public MegaRedstoneRocketItem(Properties properties) {
        super(properties);
    }

    /** A heavy shell reloads slower. */
    @Override
    public int cooldownTicks() {
        return 50;
    }

    /** Deeper launch thump than the standard rocket. */
    @Override
    public float firePitch() {
        return 0.5F;
    }

    @Override
    public void clientTrail(RedstoneRocketEntity rocket) {
        Vec3 motion = rocket.getDeltaMovement();
        for (int i = 0; i < 4; i++) {
            double back = i / 4.0;
            rocket.level().addParticle(HEAVY_TRAIL,
                    rocket.getX() - motion.x * back,
                    rocket.getY() - motion.y * back,
                    rocket.getZ() - motion.z * back,
                    0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onImpact(RedstoneRocketEntity rocket, HitResult result) {
        Level level = rocket.level();
        // ExplosionInteraction.TNT = real block destruction, TNT drop rules
        // (some destroyed blocks drop, some are consumed by the blast).
        level.explode(rocket, rocket.getX(), rocket.getY(), rocket.getZ(),
                MEGA_BLAST_POWER, Level.ExplosionInteraction.TNT);

        if (level instanceof ServerLevel serverLevel) {
            // A sphere of 250 giant red motes broadcast to every nearby player.
            serverLevel.sendParticles(NOVA_DUST,
                    rocket.getX(), rocket.getY(), rocket.getZ(),
                    250, 4.0, 4.0, 4.0, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.mega_rocket.blast")
                .withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
