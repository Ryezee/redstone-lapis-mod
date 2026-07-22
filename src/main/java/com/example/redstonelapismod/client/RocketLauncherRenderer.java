package com.example.redstonelapismod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.example.redstonelapismod.RedstoneLapisMod;

/**
 * Custom item renderer for the launcher — the same mechanism vanilla uses
 * for the trident and shield (items too 3D for a sprite). The model JSON's
 * {@code builtin/entity} parent makes vanilla apply the JSON's per-view
 * display transforms, then hand the actual drawing to this class.
 *
 * Recoil: the launcher's fire cooldown doubles as the kick timeline — right
 * after firing the cooldown fraction is 1.0 and decays to 0 over 1.5s; we
 * cube it so the barrel slams back fast and eases home. Known limit: only
 * the LOCAL player's cooldown is visible client-side, so launchers held by
 * other players don't show the kick (their cooldowns aren't synced to us).
 */
public class RocketLauncherRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(RedstoneLapisMod.MODID, "textures/entity/rocket_launcher.png");

    /** Barrel travel at full kick, in 16ths of a block. */
    private static final float MAX_KICK = 3.0F;

    private RocketLauncherModel model;

    public RocketLauncherRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // (Re)bake our layer into a usable part tree whenever resources load.
        this.model = new RocketLauncherModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(RocketLauncherModel.LAYER));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (this.model == null) {
            onResourceManagerReload(null); // first use before any reload event
        }

        float kick = 0.0F;
        boolean inHand = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        LocalPlayer player = Minecraft.getInstance().player;
        if (inHand && player != null) {
            float cooldown = player.getCooldowns().getCooldownPercent(stack.getItem(), 0.0F);
            kick = cooldown * cooldown * cooldown * MAX_KICK;
        }
        this.model.setRecoil(kick);

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F); // entity models are y-down; same flip as the trident
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(
                buffer, this.model.renderType(TEXTURE), false, stack.hasFoil());
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
