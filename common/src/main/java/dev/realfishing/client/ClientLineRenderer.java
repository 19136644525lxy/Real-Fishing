package dev.realfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.realfishing.RealFishing;
import dev.realfishing.physics.FishingLine;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 鱼线渲染：把求解器的 24 个节点做 Catmull-Rom 细分，再生成面向相机的四边形带。
 * 不负责：求解（ClientLineSim）、张力（服务端权威）。
 *
 * <p>不用 {@code RenderType.lines()}：多数驱动线宽恒为 1px，Iris 下还会被后处理吞掉。
 * 这里用 1×1 白色贴图 + entityCutoutNoCull，宽度可控且能吃到光影。
 */
public final class ClientLineRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RealFishing.MOD_ID, "textures/entity/line.png");
    private static final float HALF_WIDTH = 0.011F;
    private static final int SUBDIVISIONS = 2;
    private static final int POINTS = (FishingLine.NODES - 1) * SUBDIVISIONS + 1;
    private static final double[] px = new double[POINTS];
    private static final double[] py = new double[POINTS];
    private static final double[] pz = new double[POINTS];
    private static final double[] nx = new double[FishingLine.NODES];
    private static final double[] ny = new double[FishingLine.NODES];
    private static final double[] nz = new double[FishingLine.NODES];
    private static boolean loggedOnce;

    private ClientLineRenderer() {
    }

    /**
     * 在世界空间画线。
     *
     * @param poseStack 相机相对坐标系（Forge RenderLevelStageEvent 的栈；Fabric 端调用前自行平移）
     * @param cameraPos 相机世界坐标，用于计算朝向与偏移
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 cameraPos, float partialTick) {
        FishingLine line = ClientLineSim.line();
        if (line == null) {
            return;
        }
        if (!loggedOnce) {
            loggedOnce = true;
            RealFishing.LOGGER.info("[M2] render node0=({},{},{}) node23=({},{},{}) cam=({},{},{})",
                    line.nodeX(0), line.nodeY(0), line.nodeZ(0),
                    line.nodeX(FishingLine.NODES - 1), line.nodeY(FishingLine.NODES - 1), line.nodeZ(FishingLine.NODES - 1),
                    cameraPos.x, cameraPos.y, cameraPos.z);
        }
        buildPoints(line, partialTick);
        if (ClientLineSim.debugMarker()) {
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            org.joml.Quaternionf rot = camera.rotation();
            Vec3 look = new Vec3(camera.getLookVector());
            Vec3 right = new Vec3(camera.getLeftVector()).scale(-1.0);
            Vec3 eye = camera.getPosition();
            VertexConsumer mark = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            float[][] tests = {{0.0F, 1.0F, 1.0F, 1.0F}, {0.5F, 0.2F, 0.4F, 1.0F},
                    {1.0F, 0.2F, 1.0F, 0.2F}, {2.0F, 0.2F, 0.4F, 1.0F}};
            for (float[] test : tests) {
                Vec3 p = eye.add(look).add(right.scale(test[0]));
                drawMarker(poseStack, mark, rot, p.x, p.y, p.z, test[1], test[2], test[3]);
            }
        }

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < POINTS - 1; i++) {
            double ax = px[i];
            double ay = py[i];
            double az = pz[i];
            double bx = px[i + 1];
            double by = py[i + 1];
            double bz = pz[i + 1];

            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-6) {
                continue;
            }
            // 段方向 × 相机方向 = 屏幕上的横向偏移
            double cx = cameraPos.x - (ax + bx) * 0.5;
            double cy = cameraPos.y - (ay + by) * 0.5;
            double cz = cameraPos.z - (az + bz) * 0.5;

            double sx = dy * cz - dz * cy;
            double sy = dz * cx - dx * cz;
            double sz = dx * cy - dy * cx;
            double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (slen < 1.0e-6) {
                continue;
            }
            sx = sx / slen * HALF_WIDTH;
            sy = sy / slen * HALF_WIDTH;
            sz = sz / slen * HALF_WIDTH;

            float u = i / (float) (POINTS - 1);
            int red = i < 6 ? 255 : 232;
            int green = i < 6 ? 0 : 236;
            int blue = i < 6 ? 0 : 239;
            vertex(consumer, matrix, ax + sx, ay + sy, az + sz, u, 1.0F, red, green, blue);
            vertex(consumer, matrix, ax - sx, ay - sy, az - sz, u, 0.0F, red, green, blue);
            vertex(consumer, matrix, bx - sx, by - sy, bz - sz, u, 0.0F, red, green, blue);
            vertex(consumer, matrix, bx + sx, by + sy, bz + sz, u, 1.0F, red, green, blue);
        }
    }

    private static void drawMarker(PoseStack poseStack, VertexConsumer consumer, org.joml.Quaternionf rot,
                                   double x, double y, double z, float r, float g, float b) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(rot);
        Matrix4f m = poseStack.last().pose();
        float h = 0.25F;
        consumer.vertex(m, -h, -h, 0).color(r, g, b, 1.0F).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(0, 0, 1).endVertex();
        consumer.vertex(m, h, -h, 0).color(r, g, b, 1.0F).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(0, 0, 1).endVertex();
        consumer.vertex(m, h, h, 0).color(r, g, b, 1.0F).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(0, 0, 1).endVertex();
        consumer.vertex(m, -h, h, 0).color(r, g, b, 1.0F).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(0, 0, 1).endVertex();
        poseStack.popPose();
    }

    private static void buildPoints(FishingLine line, float partialTick) {
        double t = Mth.clamp(partialTick, 0.0F, 1.0F);
        // 先做逐节点插值，再做 Catmull-Rom 细分（源节点单独存放，避免输出覆盖输入）
        for (int i = 0; i < FishingLine.NODES; i++) {
            nx[i] = line.prevX(i) + (line.nodeX(i) - line.prevX(i)) * t;
            ny[i] = line.prevY(i) + (line.nodeY(i) - line.prevY(i)) * t;
            nz[i] = line.prevZ(i) + (line.nodeZ(i) - line.prevZ(i)) * t;
        }
        int out = 0;
        for (int i = 0; i < FishingLine.NODES - 1; i++) {
            int p0 = Math.max(i - 1, 0);
            int p1 = i;
            int p2 = i + 1;
            int p3 = Math.min(i + 2, FishingLine.NODES - 1);
            for (int s = 0; s < SUBDIVISIONS; s++) {
                double u = s / (double) SUBDIVISIONS;
                px[out] = catmull(nx[p0], nx[p1], nx[p2], nx[p3], u);
                py[out] = catmull(ny[p0], ny[p1], ny[p2], ny[p3], u);
                pz[out] = catmull(nz[p0], nz[p1], nz[p2], nz[p3], u);
                out++;
            }
        }
        px[out] = nx[FishingLine.NODES - 1];
        py[out] = ny[FishingLine.NODES - 1];
        pz[out] = nz[FishingLine.NODES - 1];
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               double x, double y, double z, float u, float v,
                               int red, int green, int blue) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(red, green, blue, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0x00F000F0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
