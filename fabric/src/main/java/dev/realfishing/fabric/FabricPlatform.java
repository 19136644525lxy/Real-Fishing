package dev.realfishing.fabric;

import dev.realfishing.platform.IPlatform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric 侧的 IPlatform 实现，只读 FabricLoader 的静态信息。
 */
public final class FabricPlatform implements IPlatform {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
