package com.example.redstonelapismod.client;

import com.example.redstonelapismod.RedstoneLapisMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The launcher's 3D shape — this mod's first code model, built exactly like
 * the chest's: a tree of named parts, each part a set of textured boxes.
 * Units are 16ths of a block; the geometry is authored around the origin and
 * the renderer positions it per view. Entity-model convention: +y points
 * DOWN (the renderer flips it, same as vanilla's trident).
 *
 * Two parts on purpose: "tube" (barrel + muzzle + band + sight) slides
 * backward for the recoil kick; "grip" stays planted in the hands.
 */
public class RocketLauncherModel extends Model {

    /** The address this model's geometry is registered under (baked at resource load). */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(RedstoneLapisMod.MODID, "rocket_launcher"), "main");

    private final ModelPart root;
    private final ModelPart tube;

    public RocketLauncherModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull); // texture holes (if any) render as holes
        this.root = root;
        this.tube = root.getChild("tube");
    }

    /** The geometry: called once per resource load to build the box tree. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("tube", CubeListBuilder.create()
                // Barrel: 4x4 bore, 22 long (z -11..11)
                .texOffs(0, 0).addBox(-2.0F, -2.0F, -11.0F, 4.0F, 4.0F, 22.0F)
                // Muzzle ring wrapping the front end
                .texOffs(0, 27).addBox(-3.0F, -3.0F, 8.0F, 6.0F, 6.0F, 3.0F)
                // Red trigger band, slightly inflated so it sits proud of the barrel
                .texOffs(42, 27).addBox(-2.0F, -2.0F, -3.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.25F))
                // Iron sight on top (-y is up in entity space)
                .texOffs(32, 27).addBox(-0.5F, -4.0F, 2.0F, 1.0F, 2.0F, 3.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("grip", CubeListBuilder.create()
                // Handle hanging below the rear half (+y is down in entity space)
                .texOffs(20, 27).addBox(-1.0F, 2.0F, -8.0F, 2.0F, 6.0F, 3.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64); // texture canvas size
    }

    /** Recoil: slide the tube assembly backward by this many 16ths (0 = at rest). */
    public void setRecoil(float kick) {
        this.tube.z = -kick;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
            int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
