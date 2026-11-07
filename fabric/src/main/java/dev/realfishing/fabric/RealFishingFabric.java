package dev.realfishing.fabric;

import dev.realfishing.RealFishing;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric 入口：注入平台实现并触发公共注册。
 * 不负责：任何玩法逻辑（全部在 common）。
 */
public final class RealFishingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RealFishing.init(new FabricPlatform());
        RealFishing.register();
    }
}
