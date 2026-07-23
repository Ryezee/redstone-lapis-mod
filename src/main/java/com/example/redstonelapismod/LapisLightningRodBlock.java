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
 * on strike, powered blockstate) and adds:
 *
 * 1. "Extremely attractive to lightning": a self-rescheduling strike timer.
 *    Placement schedules a tick; the chain re-schedules itself 8-20 seconds
 *    out, and if a thunderstorm is overhead the rod summons a real lightning
 *    bolt on itself. After every strike there is a 35% FLURRY chance that the
 *    next bolt lands only ~1-2 s later — and flurries re-roll, so a very
 *    conductive rod sometimes gets hammered over and over. Random ticks act
 *    only as a backstop that restarts a lost timer (e.g. rods placed before
 *    this version).
 * 2. A LUCK ENGINE decides each strike's payout. One uniform roll picks the
 *    tier: 1% dud (no XP, a sad fizzle), 1% jackpot (100-200 XP as a fountain
 *    of dozens of orbs plus a comically large — but completely harmless —
 *    celebration blast), and the remaining 98% ride a square-law curve:
 *    small payouts common, big ones rare, with orb count, particle count,
 *    chime volume, and pitch all scaling with the same luck value.
 *    XP is thrown as individual scattering orbs, not a merged award.
 *
 * Wiring note: the vanilla bolt entity only notifies blocks that are
 * literally minecraft:lightning_rod, so our summoned strike calls
 * {@link #onLightningStrike} directly — the same method vanilla would call,
 * overridden here to add the payout on top of the redstone pulse.
 */
public class LapisLightningRodBlock extends LightningRodBlock {

    // ---- Strike timer: every tick reschedules itself this far out ----
    /** Minimum delay between strike checks, in ticks (160 = 8 s). */
    private static final int STRIKE_DELAY_MIN = 160;
    /** Random extra delay, in ticks (up to +240 = 12 s; average interval ~14 s). */
    private static final int STRIKE_DELAY_SPREAD = 240;

    // ---- Flurries: chance that a strike is followed by another almost immediately ----
    /** Rolled after EVERY strike; successes chain, so flurry length is geometric:
     *  35% of strikes get a quick echo, 12% become 3+, ~4% become 4+. */
    private static final float FLURRY_CHANCE = 0.35F;
    /** Quick-echo delay, in ticks past the un-power tick (10 + up to 24 → next
     *  bolt lands ~1-2 s after the previous one). */
    private static final int FLURRY_DELAY_MIN = 10;
    private static final int FLURRY_DELAY_SPREAD = 25;

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
    /** Orb count for a normal strike: MIN_ORBS + luck * (MAX_ORBS - MIN_ORBS). */
    private static final int MIN_ORBS = 2;
    private static final int MAX_ORBS = 12;

    // ---- Jackpot tier ----
    /** Jackpot pays JACKPOT_XP_MIN + nextInt(JACKPOT_XP_SPREAD): 100-200 XP. */
    private static final int JACKPOT_XP_MIN = 100;
    private static final int JACKPOT_XP_SPREAD = 101;
    /** ...split across this many orbs, launched hard in every direction. */
    private static final int JACKPOT_ORBS = 60;

    /** Lapis nova on a normal strike: oversized deep-blue dust. */
    private static final DustParticleOptions LAPIS_BURST =
            new DustParticleOptions(new Vector3f(0.3F, 0.5F, 1.0F), 2.0F);

    /** Jackpot nova: brighter blue at the dust-size cap. */
    private static final DustParticleOptions JACKPOT_NOVA =
            new DustParticleOptions(new Vector3f(0.4F, 0.6F, 1.0F), 4.0F);

    public LapisLightningRodBlock(Properties properties) {
        super(properties);
    }

    /** Fresh placement starts the strike timer. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && !level.isClientSide) {
            scheduleNextCheck(level, pos, level.random);
        }
    }

    private void scheduleNextCheck(Level level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, STRIKE_DELAY_MIN + random.nextInt(STRIKE_DELAY_SPREAD));
    }

    /**
     * Two callers share this hook, told apart by the POWERED flag: vanilla
     * schedules a +8-tick "un-power" after a strike (arrives powered), and our
     * own timer chain (arrives unpowered — every delay we use exceeds the
     * 8-tick powered window, and vanilla lightning never targets modded rods).
     *
     * CRITICAL invariant: the game keeps AT MOST ONE pending scheduled tick
     * per (block, position) — extra schedule() calls are SILENTLY dropped
     * (LevelChunkTicks.ticksPerPosition dedups on position + block only).
     * So after a strike we must NOT reschedule ourselves: the strike's own
     * +8 un-power tick already owns the slot, and it carries the chain —
     * the powered branch below is where the next link is always forged.
     * (v0.5.0 got this wrong: its post-strike reschedule was dropped and the
     * chain died after every strike, leaving only the randomTick backstop.)
     *
     * The powered branch runs exactly 8 ticks after every strike, which makes
     * it the natural place to roll for a FLURRY: with luck the next bolt is
     * scheduled ~1-2 s out instead of 8-20 s — and since every strike re-rolls,
     * flurries chain into the occasional relentless barrage.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            super.tick(state, level, pos, random); // the un-power pulse ending
            if (random.nextFloat() < FLURRY_CHANCE) {
                level.scheduleTick(pos, this, FLURRY_DELAY_MIN + random.nextInt(FLURRY_DELAY_SPREAD));
            } else {
                scheduleNextCheck(level, pos, random);
            }
            return;
        }
        if (level.isThundering() && level.canSeeSky(pos.above())) {
            summonStrike(state, level, pos);
            return; // the strike's +8 un-power tick now owns the slot and carries the chain
        }
        scheduleNextCheck(level, pos, random); // quiet weather: just keep the timer alive
    }

    /** Backstop only: restarts a lost timer (pre-update rods, piston moves). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            scheduleNextCheck(level, pos, random);
        }
    }

    /** Spawns a REAL lightning bolt (thunder, flash, fire risk) and notifies ourselves. */
    private void summonStrike(BlockState state, ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos.above()));
        level.addFreshEntity(bolt);
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
        int orbs = MIN_ORBS + (int) (luck * (MAX_ORBS - MIN_ORBS));
        burstOrbs(level, top, xp, orbs, 0.25F + luck * 0.35F, random);

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

    /** 1% tier: 100-200 XP fountained as dozens of orbs, plus a huge harmless blast. */
    private static void strikeJackpot(ServerLevel level, BlockPos pos, Vec3 top, RandomSource random) {
        int xp = JACKPOT_XP_MIN + random.nextInt(JACKPOT_XP_SPREAD);
        burstOrbs(level, top, xp, JACKPOT_ORBS, 0.9F, random);

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

    /**
     * Throws totalXp as individual scattering orb entities instead of
     * ExperienceOrb.award's merged lump — visible, physical, pick-up-able.
     * Integer division against the REMAINING orb count splits the total
     * evenly with no XP lost and no zero-value orbs.
     */
    private static void burstOrbs(ServerLevel level, Vec3 top, int totalXp, int orbCount,
            float speed, RandomSource random) {
        int orbs = Math.min(orbCount, totalXp); // never more orbs than XP points
        int remaining = totalXp;
        for (int i = 0; i < orbs; i++) {
            int share = remaining / (orbs - i);
            remaining -= share;
            ExperienceOrb orb = new ExperienceOrb(level, top.x, top.y, top.z, share);
            orb.setDeltaMovement(
                    (random.nextDouble() - 0.5) * speed,
                    0.15 + random.nextDouble() * speed * 0.6,
                    (random.nextDouble() - 0.5) * speed);
            level.addFreshEntity(orb);
        }
    }
}
