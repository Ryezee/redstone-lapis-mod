package com.example.redstonelapismod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Lapis Lightning Rod — this mod's first Block. Inherits ALL copper-rod
 * behavior from vanilla (wall/floor placement, waterlogging, redstone pulse
 * on strike, powered blockstate) and adds two things:
 *
 * 1. "Extremely attractive to lightning": rather than waiting for vanilla's
 *    chunk lottery, the rod SUMMONS its own strikes — during a thunderstorm
 *    with open sky above, each random tick (avg ~1/minute at default speed)
 *    spawns a real lightning bolt on itself.
 * 2. Every strike bursts experience orbs worth a Bottle o' Enchanting
 *    (3 + nextInt(5) + nextInt(5) = 3-12, vanilla's exact formula) with a
 *    lapis dust nova and the orb-pickup chime.
 *
 * Wiring note: the vanilla bolt entity only notifies blocks that are
 * literally minecraft:lightning_rod, so our summoned strike calls
 * {@link #onLightningStrike} directly — the same method vanilla would call,
 * overridden here to add the payout on top of the redstone pulse.
 */
public class LapisLightningRodBlock extends LightningRodBlock {

    /** Lapis nova on strike: oversized deep-blue dust. */
    private static final DustParticleOptions LAPIS_BURST =
            new DustParticleOptions(new Vector3f(0.3F, 0.5F, 1.0F), 2.0F);

    public LapisLightningRodBlock(Properties properties) {
        super(properties);
    }

    /** Requires the .randomTicks() block property — set at registration. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isThundering() || !level.canSeeSky(pos.above())) {
            return; // storms only, and the sky must actually reach the rod
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos.above()));
        level.addFreshEntity(bolt); // a REAL strike: thunder, flash, fire risk nearby
        onLightningStrike(state, level, pos);
    }

    @Override
    public void onLightningStrike(BlockState state, Level level, BlockPos pos) {
        super.onLightningStrike(state, level, pos); // powered pulse + spark burst + neighbor updates
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.6, 0.0);

        // Bottle o' Enchanting's exact payout, as orbs from the rod tip.
        int xp = 3 + serverLevel.random.nextInt(5) + serverLevel.random.nextInt(5);
        ExperienceOrb.award(serverLevel, top, xp);

        serverLevel.sendParticles(LAPIS_BURST, top.x, top.y, top.z, 40, 0.4, 0.6, 0.4, 0.0);
        serverLevel.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, 1.0F, 0.8F);
    }
}
