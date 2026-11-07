package dev.realfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.realfishing.RealFishing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 世界坐标下的第一人称鱼竿渲染（AC-1）：把 {@link ClientRodPose} 的 5 个点画成 4 段
 * 面向相机的锥形四边形带，带弯曲与惯性。
 *
 * <p>不负责位姿计算，也不负责隐藏原版手部（平台层各用 mixin / 事件）。
 */
public final class ClientRodRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RealFishing.MOD_ID, "textures/entity/rod.png");
    private static final float BASE_WIDTH = 0.042F;
    private static final float TIP_WIDTH = 0.012F;

    private ClientRodRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 cameraPos, float partialTick) {
        if (!ClientRodPose.active()) {
            return;
        }
        ClientRodPose.build(partialTick);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < ClientRodPose.SEGMENTS; i++) {
            Vec3 a = ClientRodPose.point(i);
            Vec3 b = ClientRodPose.point(i + 1);
            Vec3 seg = b.subtract(a);
            if (seg.length() < 1.0e-5) {
                continue;
            }
            Vec3 mid = a.add(b).scale(0.5);
            Vec3 toCam = cameraPos.subtract(mid);
            Vec3 side = seg.cross(toCam);
            if (side.length() < 1.0e-5) {
                continue;
            }
            side = side.normalize();

            float t0 = i / (float) ClientRodPose.SEGMENTS;
            float t1 = (i + 1) / (float) ClientRodPose.SEGMENTS;
            float w0 = width(t0) * 0.5F;
            float w1 = width(t1) * 0.5F;

            vertex(consumer, matrix, a.add(side.scale(w0)), 0.0F, t0);
            vertex(consumer, matrix, a.subtract(side.scale(w0)), 1.0F, t0);
            vertex(consumer, matrix, b.subtract(side.scale(w1)), 1.0F, t1);
            vertex(consumer, matrix, b.add(side.scale(w1)), 0.0F, t1);
        }
    }

    private static float width(float t) {
        return BASE_WIDTH + (TIP_WIDTH - BASE_WIDTH) * t;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 p, float u, float v) {
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0x00F000F0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
