package com.example.redstonelapismod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Lapis Lazuli Laser — the lapis family's railgun round. No explosion, no
 * arc: it flies dead straight at nearly triple rocket speed and pays out on
 * exactly what it touches — massive single-target damage (bow-style, with
 * bonus experience bleeding from kills), or transmuting the struck block
 * into solid lapis. Made from Concentrated Lapis — expensive by design,
 * like everything lapis ([[lapis power system]] rule).
 */
public class LapisLaserItem extends RocketAmmoItem {

    /** Direct-hit damage — 10 hearts (a max-charged bow is ~9 damage). */
    public static final float BEAM_DAMAGE = 20.0F;

    /** Bonus experience dropped on a kill, on top of the mob's normal XP. */
    public static final int BONUS_XP = 10;

    /** Beam segment: bright lapis-blue dust, oversized. */
    private static final DustParticleOptions BEAM_DUST =
            new DustParticleOptions(new Vector3f(0.25F, 0.45F, 0.95F), 1.6F);

    public LapisLaserItem(Properties properties) {
        super(properties);
    }

    // --- Railgun flight profile ---

    @Override
    public float speed() {
        return 8.0F;   // vs 3.0 for rockets; hit detection raycasts, so no tunneling
    }

    @Override
    public float inaccuracy() {
        return 0.0F;   // a laser does not wobble
    }

    @Override
    public double gravity() {
        return 0.0;    // dead straight — no arc at any range
    }

    @Override
    public int lifetimeTicks() {
        return 100;    // 8 blocks/tick * 100 = plenty of reach
    }

    /** Heavy weapon, slow follow-up. */
    @Override
    public int cooldownTicks() {
        return 40;
    }

    /** The warden's sonic zap, pitched up — pure railgun. */
    @Override
    public SoundEvent fireSound() {
        return SoundEvents.WARDEN_SONIC_BOOM;
    }

    @Override
    public float firePitch() {
        return 1.6F;
    }

    /** A solid blue beam: dust every half block of travel + electric sparks. */
    @Override
    public void clientTrail(RedstoneRocketEntity rocket) {
        Vec3 motion = rocket.getDeltaMovement();
        int steps = 16;   // ~8 blocks of travel per tick -> a mote every half block
        for (int i = 0; i < steps; i++) {
            double back = (double) i / steps;
            rocket.level().addParticle(BEAM_DUST,
                    rocket.getX() - motion.x * back,
                    rocket.getY() - motion.y * back,
                    rocket.getZ() - motion.z * back,
                    0.0, 0.0, 0.0);
        }
        rocket.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                rocket.getX(), rocket.getY(), rocket.getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    public void onImpact(RedstoneRocketEntity rocket, HitResult result) {
        Level level = rocket.level();

        if (result instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            // Same damage-source family as arrows/snowballs: credited to the
            // shooter, so normal mob XP drops too — the bonus stacks on top.
            target.hurt(rocket.damageSources().thrown(rocket, rocket.getOwner()), BEAM_DAMAGE);
            if (target instanceof LivingEntity victim && victim.isDeadOrDying()
                    && level instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(serverLevel, target.position(), BONUS_XP);
            }
        } else if (result instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            // Transmute the struck block to lapis. Guard rails: never replace
            // unbreakables (bedrock: destroy speed < 0) or blocks with a brain
            // (chests etc. — transmuting one would delete its contents).
            if (state.getDestroySpeed(level, pos) >= 0 && level.getBlockEntity(pos) == null) {
                level.setBlockAndUpdate(pos, Blocks.LAPIS_BLOCK.defaultBlockState());
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(BEAM_DUST,
                    rocket.getX(), rocket.getY(), rocket.getZ(),
                    40, 0.4, 0.4, 0.4, 0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.lapis_laser.beam")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.lapis_laser.effects")
                .withStyle(ChatFormatting.BLUE));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
