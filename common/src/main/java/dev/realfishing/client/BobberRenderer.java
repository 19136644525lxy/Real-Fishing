package dev.realfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.realfishing.RealFishing;
import dev.realfishing.entity.BobberEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 浮漂渲染：面向相机的小四边形，贴一张红白漂贴图；落水后做轻微上下浮动。
 * 不负责：鱼线（M2 用相机朝向四边形带单独渲染）、手持模型（M5）。
 */
public class BobberRenderer extends EntityRenderer<BobberEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RealFishing.MOD_ID, "textures/entity/bobber.png");
    private static final float HALF_WIDTH = 0.16F;
    private static final float HALF_HEIGHT = 0.22F;

    public BobberRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.1F;
        this.shadowStrength = 0.25F;
    }

    @Override
    public void render(BobberEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));

        float bob = 0.0F;
        if (entity.isInWaterState()) {
            bob = Mth.sin((entity.tickCount + partialTick) * 0.25F) * 0.02F;
        }
        poseStack.translate(0.0F, HALF_HEIGHT + 0.02F + bob, 0.0F);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        vertex(consumer, matrix, pose, -HALF_WIDTH, -HALF_HEIGHT, 0.0F, 1.0F, packedLight);
        vertex(consumer, matrix, pose, HALF_WIDTH, -HALF_HEIGHT, 1.0F, 1.0F, packedLight);
        vertex(consumer, matrix, pose, HALF_WIDTH, HALF_HEIGHT, 1.0F, 0.0F, packedLight);
        vertex(consumer, matrix, pose, -HALF_WIDTH, HALF_HEIGHT, 0.0F, 0.0F, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                               float x, float y, float u, float v, int light) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BobberEntity entity) {
        return TEXTURE;
    }
}
