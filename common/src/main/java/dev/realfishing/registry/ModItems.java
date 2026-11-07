package dev.realfishing.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.realfishing.RealFishing;
import dev.realfishing.item.FloatRodItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * 物品注册表。v0.1 只注册 float_rod；lure_rod 按 AGENTS §9 留到 M9，现在不得出现。
 * 不负责：物品行为（在 item 包）。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(RealFishing.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> FLOAT_ROD =
            ITEMS.register("float_rod", FloatRodItem::new);

    private ModItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
