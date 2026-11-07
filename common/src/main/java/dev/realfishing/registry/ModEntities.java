package dev.realfishing.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.realfishing.RealFishing;
import dev.realfishing.entity.BobberEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 实体注册表。M0 只注册空壳 bobber，物理与同步在 M1/M2 补。
 * 不负责：浮漂的生成与销毁（由会话驱动，M2）。
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(RealFishing.MOD_ID, Registries.ENTITY_TYPE);

    // noSave：浮漂不落盘，避免玩家退出后留下幽灵实体；同时跳过 datafixer 查表
    public static final RegistrySupplier<EntityType<BobberEntity>> BOBBER =
            ENTITY_TYPES.register("bobber", () -> EntityType.Builder
                    .<BobberEntity>of(BobberEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .noSave()
                    .build("realfishing:bobber"));

    private ModEntities() {
    }

    public static void register() {
        ENTITY_TYPES.register();
    }
}
