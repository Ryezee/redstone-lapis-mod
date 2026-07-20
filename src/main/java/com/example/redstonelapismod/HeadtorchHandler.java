package com.example.redstonelapismod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Projects the headtorch's light where the wearer is looking — with PURE VANILLA
 * mechanics: we keep one invisible {@code minecraft:light} block (the mapmaker
 * light block: no collision, invisible, emits light) at the surface the player
 * faces, and move it as they turn. The lighting engine does the rest, so this
 * works with shaders and needs no dynamic-lights mod.
 *
 * Safety rules: we only ever place into pure air, we only ever remove a block
 * if it is still a light block, and we track exactly one spot per player
 * (dimension-aware via {@link GlobalPos}), removed on unequip/power-loss/logout.
 * Accepted v1 limitation: a hard server crash can strand one invisible light
 * block per player — inert and harmless.
 */
public final class HeadtorchHandler {
    /** How far ahead (blocks) the torch can throw its spot. */
    private static final int SPOT_RANGE = 8;
    /** Light level of the projected spot (redstone torch is 7, torch 14). */
    private static final int SPOT_LIGHT_LEVEL = 14;
    /** Recompute the spot every N ticks (1 = every tick, 20 updates per second).
     *  Safe: block/light work only happens when the target block actually changes
     *  (see moveSpot's dedup), so idle cost is just one cheap raycast per tick. */
    private static final int SPOT_UPDATE_TICKS = 1;
    /** Bill 1 charge every N ticks (40 = half the goggles' drain rate). */
    private static final int DRAIN_INTERVAL_TICKS = 40;

    /** The one projected spot per player, remembered so we can clean it up. */
    private static final Map<UUID, GlobalPos> SPOTS = new HashMap<>();

    private HeadtorchHandler() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return; // server-side only; the client sees the result via normal block sync
        }

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean lit = head.is(RedstoneLapisMod.REDSTONE_MINER_HEADTORCH.get())
                && PoweredHeadgearItem.isPowered(player, head, 1);

        if (lit && player.tickCount % DRAIN_INTERVAL_TICKS == 0) {
            PoweredHeadgearItem.drainOnePowerTick(player, head, 1);
        }

        if (player.tickCount % SPOT_UPDATE_TICKS != 0) {
            return; // throttle the raycast + block moves
        }

        BlockPos target = lit ? findSpotTarget(serverPlayer) : null;
        moveSpot(serverPlayer, target);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            removeSpot(serverPlayer.getServer(), serverPlayer.getUUID());
        }
    }

    /**
     * Raycasts along the look vector and returns the air block just in front of
     * the surface being looked at (or the far end of the ray in open space).
     * Returns null when there is no valid spot (e.g. the space is occupied).
     */
    private static BlockPos findSpotTarget(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SPOT_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        BlockPos pos = hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos().relative(hit.getDirection()) // air block touching the hit face
                : BlockPos.containing(end);                      // open space: end of the ray

        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.LIGHT)) ? pos : null;
    }

    /** Moves this player's spot to {@code newPos} (null = just remove it). */
    private static void moveSpot(ServerPlayer player, BlockPos newPos) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        GlobalPos old = SPOTS.get(id);
        GlobalPos target = newPos == null ? null : GlobalPos.of(level.dimension(), newPos);

        if (old != null && !old.equals(target)) {
            removeSpot(player.getServer(), id);
        }
        if (target == null) {
            return;
        }

        SPOTS.put(id, target);
        if (level.getBlockState(newPos).isAir()) { // don't re-place if our light is already there
            level.setBlock(newPos,
                    Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, SPOT_LIGHT_LEVEL),
                    Block.UPDATE_ALL);
        }
    }

    /** Removes a player's tracked spot, only if the block there is still a light block. */
    private static void removeSpot(MinecraftServer server, UUID id) {
        GlobalPos tracked = SPOTS.remove(id);
        if (tracked == null) {
            return;
        }
        ServerLevel level = server.getLevel(tracked.dimension());
        if (level != null && level.getBlockState(tracked.pos()).is(Blocks.LIGHT)) {
            level.removeBlock(tracked.pos(), false);
        }
    }
}
