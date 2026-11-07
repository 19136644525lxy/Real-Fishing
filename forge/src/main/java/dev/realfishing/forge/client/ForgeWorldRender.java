package dev.realfishing.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.realfishing.RealFishing;
import dev.realfishing.client.ClientLineRenderer;
import dev.realfishing.client.ClientLineSim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/**
 * Forge 端世界渲染钩子：在半透明方块之后画鱼线。
 * 不负责：线的求解与顶点生成（都在 common 的 client 包里）。
 */
public final class ForgeWorldRender {
    private static boolean loggedPose;

    private ForgeWorldRender() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ForgeWorldRender.class);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        if (!loggedPose) {
            loggedPose = true;
            Vector3f t = poseStack.last().pose().getTranslation(new Vector3f());
            RealFishing.LOGGER.info("[M2] forge pose translation={} camera={}", t, camera);
        }
        if (ClientLineSim.line() == null) {
            return;
        }
        poseStack.pushPose();
        // Forge 这个阶段的世界矩阵原点在 (0,0,0) 还是相机，取决于版本；先按相机相对处理，
        // 若日志里 translation ≈ -camera 就说明已经是相对坐标，这里的 translate 要去掉。
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        ClientLineRenderer.render(poseStack,
                Minecraft.getInstance().renderBuffers().bufferSource(), camera, event.getPartialTick());
        poseStack.popPose();
    }
}
