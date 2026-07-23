package com.example.redstonelapismod;

import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Lapis Paragon — the lapis family's legendary capstone. Loot-only, ultra
 * rare. Click it onto enchanted gear (or an enchanted book) in the
 * inventory: every enchantment at (or already beyond) its vanilla maximum
 * rises one more level (Sharpness V -> VI -> VII -> ...). No upper limit —
 * rarity is the only brake (loot rolls one gem at a time). Below-max
 * enchantments are untouched: max it the vanilla way first. Consumed on use.
 *
 * Gesture note: batteries/fuel use overrideOtherStackedOnMe on the RECEIVING
 * item, but swords are vanilla code — so the Paragon uses the mirror hook,
 * overrideStackedOnOther: "I'm on the cursor and was clicked onto a slot."
 */
public class LapisParagonItem extends Item {

    public LapisParagonItem(Properties properties) {
        super(properties);
    }

    /** Always shimmer like an enchanted item — it is one, in spirit. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack paragon, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false; // left-click keeps vanilla pick-up/swap behavior
        }
        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            return false;
        }

        // Books store their enchantments under a different component key.
        DataComponentType<ItemEnchantments> componentType = target.is(Items.ENCHANTED_BOOK)
                ? DataComponents.STORED_ENCHANTMENTS
                : DataComponents.ENCHANTMENTS;
        ItemEnchantments current = target.getOrDefault(componentType, ItemEnchantments.EMPTY);
        if (current.isEmpty()) {
            return false; // not enchanted — let the click behave normally
        }

        ItemEnchantments.Mutable ascended = new ItemEnchantments.Mutable(current);
        int lifted = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : current.entrySet()) {
            int level = entry.getIntValue();
            // At or beyond vanilla max -> one more. Below max: untouched
            // (earn the vanilla cap first). No ceiling — user rule; the
            // component codec tops out at 255, far past any sane grind.
            if (level >= entry.getKey().value().getMaxLevel()) {
                ascended.set(entry.getKey(), level + 1);
                lifted++;
            }
        }

        if (lifted == 0) {
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
            return true; // eat the click, refuse politely, keep the gem
        }

        target.set(componentType, ascended.toImmutable());
        paragon.shrink(1);
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.9F, 1.4F);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7F, 1.6F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.redstonelapismod.paragon.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.paragon.effect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.redstonelapismod.paragon.hint")
                .withStyle(ChatFormatting.BLUE));
    }
}
