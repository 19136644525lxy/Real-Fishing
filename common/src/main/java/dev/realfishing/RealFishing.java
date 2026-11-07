package dev.realfishing;

import dev.realfishing.command.RealFishingCommands;
import dev.realfishing.platform.IPlatform;
import dev.realfishing.platform.Platform;
import dev.realfishing.registry.ModEntities;
import dev.realfishing.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组公共入口：持有 modId / 版本 / 平台实现，把各注册表接起来。
 * 不负责：平台特有的初始化（入口类在 fabric、forge 模块里）。
 */
public final class RealFishing {
    public static final String MOD_ID = "realfishing";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private RealFishing() {
    }

    /** 由平台入口在加载期调用一次，注入平台实现。 */
    public static void init(IPlatform platform) {
        Platform.set(platform);
        LOGGER.info("Real Fishing {} 初始化，loader={}", VERSION, platform.loaderName());
    }

    /** 注册物品 / 实体 / 命令等公共内容。 */
    public static void register() {
        ModItems.register();
        ModEntities.register();
        RealFishingCommands.register();
    }
}
