package com.example.redstonelapismod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
 * 2. A LUCK ENGINE decides each strike's payout. One uniform roll picks the
 *    tier: 1% dud (no XP, a sad fizzle), 1% jackpot (100-200 XP and a
 *    comically large — but completely harmless — celebration blast), and the
 *    remaining 98% ride a square-law curve: small payouts common, big ones
 *    rare, with particle count, chime volume, and pitch all scaling with the
 *    same luck value so a lucky strike LOOKS and SOUNDS lucky.
 *
 * Wiring note: the vanilla bolt entity only notifies blocks that are
 * literally minecraft:lightning_rod, so our summoned strike calls
 * {@link #onLightningStrike} directly — the same method vanilla would call,
 * overridden here to add the payout on top of the redstone pulse.
 */
public class LapisLightningRodBlock extends LightningRodBlock {

    // ---- Tier odds: one nextFloat() roll per strike, read against these bands ----
    /** 1% of strikes yield nothing at all. */
    private static final float DUD_CHANCE = 0.01F;
    /** 1% of strikes are the jackpot. */
    private static final float JACKPOT_CHANCE = 0.01F;

    // ---- Normal tier: XP spans this range, position on it chosen by squared luck ----
    private static final int MIN_XP = 1;
    private static final int MAX_XP = 30;
    /** Squared-luck threshold for the level-up flourish — only the top ~5% of strikes. */
    private static final float GREAT_HIT_LUCK = 0.9F;
    /** Dust mote count also rides the luck curve, between these bounds. */
    private static final int MIN_MOTES = 8;
    private static final int MAX_MOTES = 120;

    // ---- Jackpot tier ----
    /** Jackpot pays JACKPOT_XP_MIN + nextInt(JACKPOT_XP_SPREAD): 100-200 XP. */
    private static final int JACKPOT_XP_MIN = 100;
    private static final int JACKPOT_XP_SPREAD = 101;

    /** Lapis nova on a normal strike: oversized deep-blue dust. */
    private static final DustParticleOptions LAPIS_BURST =
            new DustParticleOptions(new Vector3f(0.3F, 0.5F, 1.0F), 2.0F);

    /** Jackpot nova: brighter blue at the dust-size cap. */
    private static final DustParticleOptions JACKPOT_NOVA =
            new DustParticleOptions(new Vector3f(0.4F, 0.6F, 1.0F), 4.0F);

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
        RandomSource random = serverLevel.random;

        float roll = random.nextFloat(); // uniform [0, 1) — one roll picks the tier
        if (roll < DUD_CHANCE) {
            strikeDud(serverLevel, pos, top);
        } else if (roll < DUD_CHANCE + JACKPOT_CHANCE) {
            strikeJackpot(serverLevel, pos, top, random);
        } else {
            strikeNormal(serverLevel, pos, top, random);
        }
    }

    /** 98% tier: payout and presentation both scale with one squared-luck value. */
    private static void strikeNormal(ServerLevel level, BlockPos pos, Vec3 top, RandomSource random) {
        // Squaring a uniform roll crowds results toward 0: median luck drops to
        // 0.25 and only ~10% of strikes land above 0.68 — small payouts common,
        // big ones rare. This one number then drives EVERYTHING below, so a
        // strike's look and sound always match its generosity.
        float luck = random.nextFloat();
        luck = luck * luck;

        int xp = MIN_XP + Math.round(luck * (MAX_XP - MIN_XP));
        ExperienceOrb.award(level, top, xp);

        int motes = MIN_MOTES + (int) (luck * (MAX_MOTES - MIN_MOTES));
        level.sendParticles(LAPIS_BURST, top.x, top.y, top.z, motes, 0.4, 0.6, 0.4, 0.0);

        float volume = 0.4F + luck;         // 0.4 (meh) .. 1.4 (jubilant)
        float pitch = 0.6F + luck * 0.6F;   // luckier = brighter chime
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, volume, pitch);

        if (luck >= GREAT_HIT_LUCK) { // top ~5% of normal strikes get a flourish
            level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 25, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP,
                    SoundSource.BLOCKS, volume, 1.4F);
        }
    }

    /** 1% tier: 100-200 XP and a huge celebration blast — zero damage of any kind. */
    private static void strikeJackpot(ServerLevel level, BlockPos pos, Vec3 top, RandomSource random) {
        ExperienceOrb.award(level, top, JACKPOT_XP_MIN + random.nextInt(JACKPOT_XP_SPREAD));

        // Purely audiovisual explosion — no Level.explode, so nothing is hurt.
        // Each EXPLOSION_EMITTER blooms into ~100 blast puffs; stack a column of three.
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    top.x, top.y + i * 2.0, top.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(JACKPOT_NOVA, top.x, top.y, top.z, 250, 3.0, 3.0, 3.0, 0.0);
        level.sendParticles(ParticleTypes.FIREWORK, top.x, top.y + 1.0, top.z, 150, 1.5, 2.5, 1.5, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 80, 2.0, 3.0, 2.0, 0.1);

        // Layered fanfare. GENERIC_EXPLODE is a Holder in 1.21 — hence .value().
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.5F);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 3.0F, 0.8F);
        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.5F, 1.0F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 2.0F, 0.5F);
    }

    /** 1% tier: no XP whatsoever. The tiny fizzle is deliberate feedback so a
     *  dud reads as bad luck rather than a bug — delete both lines for true silence. */
    private static void strikeDud(ServerLevel level, BlockPos pos, Vec3 top) {
        level.sendParticles(ParticleTypes.SMOKE, top.x, top.y, top.z, 6, 0.1, 0.2, 0.1, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F);
    }
}
