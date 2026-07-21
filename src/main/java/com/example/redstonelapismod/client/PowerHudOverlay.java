package com.example.redstonelapismod.client;

import com.example.redstonelapismod.BatteryItem;
import com.example.redstonelapismod.PoweredGearItem;
import com.example.redstonelapismod.RedstoneLapisMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The battery gauge: one pooled bar per POWER TYPE, drawn just above the
 * hotbar while at least one powered gadget is worn. The red bar totals every
 * drop of REDSTONE charge the player can actually spend — batteries socketed
 * in worn gear plus loose batteries in the inventory (the same pool the
 * drain-priority logic in PoweredGearItem walks). Built type-generic on
 * purpose: when lapis gadgets exist, their blue bar is one more
 * {@code drawPowerBar} call with a different pool + color.
 *
 * Same HUD-layer mechanism as {@link GoggleOverlay}, but registered ABOVE the
 * vanilla HUD — a gauge must sit on top to be readable.
 */
@EventBusSubscriber(modid = RedstoneLapisMod.MODID, value = Dist.CLIENT)
public final class PowerHudOverlay {

    /** Bar geometry (GUI pixels): width, height, and lift above the hotbar cluster. */
    private static final int BAR_WIDTH = 62;
    private static final int BAR_HEIGHT = 3;
    /** Vertical position: bottom of screen minus this. Clears hotbar+exp+armor/food rows. */
    private static final int BAR_BOTTOM_OFFSET = 54;

    /** Fill colors per power type (0xAARRGGBB). */
    private static final int REDSTONE_FILL = 0xFFE83232;   // matches the item charge bars
    private static final int BACKGROUND = 0xAA141414;
    private static final int CHARGE_INDICATOR_FILL = 0xFFFFE080; // white-hot blast charge sliver

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "power_hud");

    /** Worn-gear slots that can hold a powered gadget. */
    private static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private PowerHudOverlay() {}

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        // registerAboveAll -> drawn on top of the vanilla HUD (a gauge must be readable).
        event.registerAboveAll(LAYER_ID, PowerHudOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            return;
        }

        // Gauge only appears while a powered gadget is actually worn.
        boolean wearingGadget = false;
        int charge = 0;
        int capacity = 0;
        for (EquipmentSlot slot : GEAR_SLOTS) {
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.getItem() instanceof PoweredGearItem) {
                wearingGadget = true;
                if (PoweredGearItem.hasBatteryInstalled(gear)) {
                    charge += PoweredGearItem.installedCharge(gear);
                    capacity += BatteryItem.MAX_CHARGE;
                }
            }
        }
        if (!wearingGadget) {
            return;
        }

        // Pocket batteries join the pool — they're real spendable charge (the
        // drain logic falls back to them), and each adds its 1000 of capacity.
        // The inventory container covers main + armor + offhand; armor slots
        // hold gear (not BatteryItem), so nothing is double-counted.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof BatteryItem) {
                charge += BatteryItem.getCharge(stack);
                capacity += BatteryItem.MAX_CHARGE;
            }
        }

        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = graphics.guiHeight() - BAR_BOTTOM_OFFSET;
        drawPowerBar(graphics, x, y, capacity == 0 ? 0f : (float) charge / capacity, REDSTONE_FILL);

        // While a boot blast is charging, a bright sliver grows above the bar.
        float blastCharge = RocketBootsClientHandler.chargeFraction();
        if (blastCharge > 0f) {
            int cw = Math.round((BAR_WIDTH / 2f) * blastCharge);
            int cx = graphics.guiWidth() / 2 - cw / 2;
            graphics.fill(cx, y - 4, cx + cw, y - 2, CHARGE_INDICATOR_FILL);
        }
    }

    /** One power bar: dark backdrop + colored fill. Reusable for future (blue) lapis power. */
    private static void drawPowerBar(GuiGraphics graphics, int x, int y, float fraction, int fillColor) {
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BACKGROUND);
        int w = Math.round(BAR_WIDTH * Math.min(1f, Math.max(0f, fraction)));
        if (w > 0) {
            graphics.fill(x, y, x + w, y + BAR_HEIGHT, fillColor);
        }
    }
}
