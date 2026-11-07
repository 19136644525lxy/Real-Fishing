package dev.realfishing.forge;

import dev.realfishing.platform.IPlatform;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Forge 侧的 IPlatform 实现，只读 FML 的静态信息。
 */
public final class ForgePlatform implements IPlatform {
    @Override
    public String loaderName() {
        return "forge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
