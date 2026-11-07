package dev.realfishing.forge;

import dev.architectury.platform.forge.EventBuses;
import dev.realfishing.RealFishing;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge 入口：构造期注入平台实现并触发公共注册（Forge 在 mod 构造时注册内容）。
 * 不负责：任何玩法逻辑（全部在 common）。
 */
@Mod(RealFishing.MOD_ID)
public final class RealFishingForge {
    public RealFishingForge(FMLJavaModLoadingContext context) {
        // Architectury 的 DeferredRegister 需要按 modId 拿到本 mod 的 ModEventBus；
        // Fabric 端只有一条总线，Forge 端必须显式登记，否则 register() 抛
        // "Can't get event bus for mod ... because it was not registered!"。
        EventBuses.registerModEventBus(RealFishing.MOD_ID, context.getModEventBus());
        RealFishing.init(new ForgePlatform());
        RealFishing.register();
    }
}
