package dev.realfishing.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.realfishing.RealFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 音效注册表（M4）：抛投、入水、咬钩、收线、断线、上鱼六个自定义音效。
 * 不负责播放策略（在 FishingManager / BobberEntity / 客户端）。
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(RealFishing.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> CAST = register("cast");
    public static final RegistrySupplier<SoundEvent> LAND = register("land");
    public static final RegistrySupplier<SoundEvent> BITE = register("bite");
    public static final RegistrySupplier<SoundEvent> REEL = register("reel");
    public static final RegistrySupplier<SoundEvent> SNAP = register("snap");
    public static final RegistrySupplier<SoundEvent> CATCH = register("catch");

    private ModSounds() {
    }

    private static RegistrySupplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(RealFishing.MOD_ID, name)));
    }

    public static void register() {
        SOUNDS.register();
    }
}
