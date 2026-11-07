package dev.realfishing.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.realfishing.client.ClientLineRenderer;
import dev.realfishing.client.ClientLineSim;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric 端世界渲染钩子：把鱼线画在水面/半透明层之后。
 * 不负责：线的求解与顶点生成（都在 common 的 client 包里）。
 */
public final class FabricWorldRender implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FabricWorldRender::afterTranslucent);
    }

    private static void afterTranslucent(WorldRenderContext context) {
        if (ClientLineSim.line() == null || context.consumers() == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        // Fabric 的栈是"世界原点"，先平移到相机相对坐标再画
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        ClientLineRenderer.render(poseStack, context.consumers(), camera, context.tickDelta());
        poseStack.popPose();
    }
}
