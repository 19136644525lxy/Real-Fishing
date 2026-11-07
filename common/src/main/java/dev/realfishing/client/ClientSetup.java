package dev.realfishing.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.realfishing.registry.ModEntities;

/**
 * 客户端注册入口：实体渲染器、HUD、预测替身 tick。只在客户端调用（由 RealFishing.register 判 isClient）。
 * 不负责：平台相关初始化（fabric/forge 的 ClientModInitializer 另有入口）。
 */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register() {
        EntityRendererRegistry.register(ModEntities.BOBBER, BobberRenderer::new);
        ClientGuiEvent.RENDER_HUD.register(FishingHud::render);
        ClientTickEvent.CLIENT_POST.register(ClientCast::clientTick);
        ClientTickEvent.CLIENT_POST.register(ClientLineSim::tick);
        ClientTickEvent.CLIENT_POST.register(DebugAutoCast::tick);
    }
}
