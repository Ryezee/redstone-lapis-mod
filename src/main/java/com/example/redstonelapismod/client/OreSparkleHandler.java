package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * Passive ore "sparkle" for the Redstone Goggles: while the goggles are worn, nearby ore blocks
 * shed faint redstone-red dust motes from their exposed faces, so ores catch the eye in the dark
 * without any keybind and without revealing anything through walls.
 *
 * <p>Everything here is client-side and cosmetic. Ores are matched generically via the
 * {@code c:ores} tag ({@link Tags.Blocks#ORES}) so ore-adding mods are covered automatically.
 * Particles only spawn on faces that touch a non-solid block, so a fully buried ore stays hidden —
 * no x-ray. Rendering goes through the vanilla particle engine, which shader packs (Iris/Photon)
 * support natively, so there is no custom render state to conflict with.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class OreSparkleHandler {
    // --- Tunables ---

    /** Sphere radius (blocks) checked around the player each pulse. */
    private static final int SCAN_RADIUS = 12;
    /** Ticks between pulses (20 ticks = 1 second). */
    private static final int PULSE_INTERVAL_TICKS = 10;
    /** Chance for a given exposed ore to sparkle on a given pulse (keeps it a shimmer, not a strobe). */
    private static final float SPARKLE_CHANCE = 0.35f;
    /** Safety cap on particles per pulse, to bound the cost near huge ore veins. */
    private static final int MAX_SPARKLES_PER_PULSE = 80;
    /** Sparkle size; color now comes per-ore from {@link OreColors} (texture accent sampling). */
    private static final float SPARKLE_SCALE = 1.0f;

    private static int ticksUntilPulse = PULSE_INTERVAL_TICKS;

    private OreSparkleHandler() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (--ticksUntilPulse > 0) {
            return;
        }
        ticksUntilPulse = PULSE_INTERVAL_TICKS;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !GogglesClient.isPowered(mc.player)) {
            return;
        }

        RandomSource rng = level.random;
        BlockPos center = mc.player.blockPosition();
        int r = SCAN_RADIUS;
        int rSq = r * r;
        int spawned = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) {
                        continue; // keep the scan spherical
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor.getY())) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Tags.Blocks.ORES) || rng.nextFloat() > SPARKLE_CHANCE) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        neighbor.setWithOffset(cursor, dir);
                        BlockState neighborState = level.getBlockState(neighbor);
                        if (neighborState.isSolidRender(level, neighbor)) {
                            continue; // face is buried; sparkling here would be invisible x-ray bait
                        }
                        // Spawn just off the exposed face, jittered so veins shimmer irregularly.
                        double px = cursor.getX() + 0.5 + dir.getStepX() * 0.55 + (rng.nextDouble() - 0.5) * 0.5;
                        double py = cursor.getY() + 0.5 + dir.getStepY() * 0.55 + (rng.nextDouble() - 0.5) * 0.5;
                        double pz = cursor.getZ() + 0.5 + dir.getStepZ() * 0.55 + (rng.nextDouble() - 0.5) * 0.5;
                        level.addParticle(OreColors.dustFor(state, SPARKLE_SCALE), px, py, pz, 0.0, 0.0, 0.0);
                        spawned++;
                        break; // one mote per ore per pulse is plenty
                    }
                    if (spawned >= MAX_SPARKLES_PER_PULSE) {
                        break outer;
                    }
                }
            }
        }
    }
}
