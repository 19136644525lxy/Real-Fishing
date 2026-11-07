package dev.realfishing.forge.client;

import dev.realfishing.client.ClientLineRenderer;
import dev.realfishing.client.ClientLineSim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 端世界渲染钩子：在液体/半透明方块之后画鱼线。
 * 不负责：线的求解与顶点生成（都在 common 的 client 包里）。
 */
public final class ForgeWorldRender {
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
        if (ClientLineSim.line() == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        // Forge 的栈已经是相机相对坐标（原点在相机），不需要再平移
        ClientLineRenderer.render(event.getPoseStack(),
                Minecraft.getInstance().renderBuffers().bufferSource(), camera, event.getPartialTick());
    }
}
